package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.model.Port;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.List;
import java.util.Optional;

/**
 * What a router does: accept frames addressed to one of its interfaces, answer ARP and pings for
 * its own addresses, and forward everything else by longest-prefix match on its connected routes,
 * subtracting one from the TTL and building a new frame for the next hop. It never forwards
 * broadcasts and tells the sender when it must drop a packet (ICMP time exceeded, unreachable).
 */
final class RouterBehavior {

  private RouterBehavior() {}

  static void receive(Simulation sim, Router router, Port in, EthernetFrame frame) {
    sim.emit(
        router,
        in,
        2,
        SimAction.RECEIVE,
        frame,
        ExplanationKey.L2_FRAME_RECEIVED,
        in.name(),
        frame.srcMac(),
        frame.dstMac());
    MacAddress ownMac = in.mac().orElseThrow();
    boolean broadcast = frame.dstMac().isBroadcast();
    if (!broadcast && !frame.dstMac().equals(ownMac)) {
      sim.emit(
          router,
          in,
          2,
          SimAction.DROP,
          frame,
          ExplanationKey.NIC_FRAME_NOT_FOR_ME,
          frame.dstMac(),
          ownMac);
      return;
    }
    if (!in.hasIpv4()) {
      sim.emit(
          router, in, 2, SimAction.DROP, frame, ExplanationKey.ROUTER_INTERFACE_NO_IP, in.name());
      sim.fail(FailureReason.NO_IP_ADDRESS, router);
      return;
    }
    if (broadcast) {
      sim.emit(router, in, 2, SimAction.DECAPSULATE, frame, ExplanationKey.NIC_ACCEPTED_BROADCAST);
    } else {
      sim.emit(
          router,
          in,
          2,
          SimAction.DECAPSULATE,
          frame,
          ExplanationKey.NIC_ACCEPTED_UNICAST,
          frame.dstMac());
    }
    if (frame.payload() instanceof ArpMessage arp) {
      sim.handleArp(router, in, arp, true);
    } else if (frame.payload() instanceof Ipv4Packet packet) {
      receivePacket(sim, router, in, frame, packet, broadcast);
    }
  }

  private static void receivePacket(
      Simulation sim,
      Router router,
      Port in,
      EthernetFrame frame,
      Ipv4Packet packet,
      boolean broadcast) {
    if (isOwnAddress(router, packet.dst())) {
      receiveLocal(sim, router, in, packet);
    } else if (broadcast) {
      // A frame sent to the broadcast MAC address stops at the router, whatever it carries.
      sim.emit(
          router, in, 2, SimAction.DROP, packet, ExplanationKey.ROUTER_BROADCAST_NOT_FORWARDED);
    } else {
      forward(sim, router, in, frame, packet);
    }
  }

  private static boolean isOwnAddress(Router router, Ipv4Address address) {
    for (Port port : router.ports()) {
      if (port.isLinkUp() && port.ip().filter(address::equals).isPresent()) {
        return true;
      }
    }
    return false;
  }

  /** The packet is for the router itself: answer an echo request. */
  private static void receiveLocal(Simulation sim, Router router, Port in, Ipv4Packet packet) {
    IcmpMessage icmp = (IcmpMessage) packet.payload();
    if (icmp.type() != IcmpType.ECHO_REQUEST) {
      sim.emit(router, in, 3, SimAction.DROP, packet, ExplanationKey.ICMP_UNEXPECTED, packet.src());
      return;
    }
    sim.emit(
        router,
        in,
        3,
        SimAction.DELIVER,
        packet,
        ExplanationKey.ICMP_ECHO_REQUEST_RECEIVED,
        packet.src(),
        icmp.seq());
    IcmpMessage echoReply = new IcmpMessage(IcmpType.ECHO_REPLY, 0, icmp.id(), icmp.seq());
    // The reply comes from the address that was pinged, even if it is another interface's.
    Ipv4Packet reply =
        Ipv4Packet.icmp(packet.dst(), packet.src(), sim.settings().routerTtl(), echoReply);
    sim.emit(
        router,
        null,
        3,
        SimAction.GENERATE,
        reply,
        ExplanationKey.ICMP_ECHO_REPLY_CREATED,
        packet.src(),
        icmp.seq());
    originate(sim, router, reply);
  }

  /** Routes a packet that a router itself created (a reply or an error). */
  private static void originate(Simulation sim, Router router, Ipv4Packet packet) {
    Optional<Route> route =
        RoutingTables.longestMatch(RoutingTables.connected(router), packet.dst());
    if (route.isEmpty()) {
      sim.emit(
          router,
          null,
          3,
          SimAction.DROP,
          packet,
          ExplanationKey.ROUTER_NO_ROUTE_TO_SENDER,
          packet.dst());
      return;
    }
    Port out = router.port(route.get().interfaceName());
    sim.emit(
        router,
        out,
        3,
        SimAction.LOOKUP,
        packet,
        ExplanationKey.ROUTER_ROUTE_FOUND,
        packet.dst(),
        route.get().destination(),
        out.name());
    sim.sendIpv4(router, out, packet.dst(), packet, null, false, null);
  }

  /** Forwards a transit packet: TTL, route lookup, new frame for the next hop. */
  private static void forward(
      Simulation sim, Router router, Port in, EthernetFrame frame, Ipv4Packet packet) {
    int newTtl = packet.ttl() - 1;
    Ipv4Packet decremented = packet.withTtl(Math.max(newTtl, 0));
    sim.emit(
        router,
        in,
        3,
        SimAction.DECREMENT_TTL,
        decremented,
        ExplanationKey.ROUTER_TTL_DECREMENT,
        packet.ttl(),
        decremented.ttl());
    if (newTtl <= 0) {
      sim.emit(
          router,
          in,
          3,
          SimAction.DROP,
          decremented,
          ExplanationKey.ROUTER_TTL_EXPIRED,
          packet.src(),
          packet.dst());
      sim.fail(FailureReason.TTL_EXPIRED, router);
      sendError(sim, router, in, packet, IcmpType.TIME_EXCEEDED, 0);
      return;
    }

    List<Route> routes = RoutingTables.connected(router);
    Optional<Route> route = RoutingTables.longestMatch(routes, packet.dst());
    if (route.isEmpty()) {
      dropNoRoute(sim, router, in, decremented);
      return;
    }
    Port out = router.port(route.get().interfaceName());
    sim.emit(
        router,
        out,
        3,
        SimAction.LOOKUP,
        decremented,
        ExplanationKey.ROUTER_ROUTE_FOUND,
        packet.dst(),
        route.get().destination(),
        out.name());
    sim.sendIpv4(router, out, packet.dst(), decremented, frame, false, in);
  }

  /**
   * No route. If an interface of the matching network exists but is not up/up, say so: the usual
   * cause in the labs is a forgotten {@code no shutdown}.
   */
  private static void dropNoRoute(Simulation sim, Router router, Port in, Ipv4Packet packet) {
    Port downInterface = null;
    for (Port port : router.ports()) {
      if (port.type().isEthernet()
          && port.network().filter(network -> network.contains(packet.dst())).isPresent()
          && !port.isLinkUp()) {
        downInterface = port;
        break;
      }
    }
    if (downInterface != null) {
      ExplanationKey why =
          downInterface.isAdminUp()
              ? ExplanationKey.ROUTER_NO_ROUTE_INTERFACE_DOWN
              : ExplanationKey.ROUTER_NO_ROUTE_INTERFACE_SHUTDOWN;
      sim.emit(router, in, 3, SimAction.DROP, packet, why, packet.dst(), downInterface.name());
      sim.fail(FailureReason.INTERFACE_DOWN, router);
    } else {
      sim.emit(router, in, 3, SimAction.DROP, packet, ExplanationKey.ROUTER_NO_ROUTE, packet.dst());
      sim.fail(FailureReason.NO_ROUTE, router);
    }
    sendError(sim, router, in, packet, IcmpType.DESTINATION_UNREACHABLE, 0);
  }

  /**
   * Tells the sender of {@code original} why it was dropped. The message comes from the address of
   * the interface the packet arrived on. Errors are never sent about ICMP errors, which would
   * otherwise ping-pong forever.
   */
  static void sendError(
      Simulation sim, Router router, Port ingress, Ipv4Packet original, IcmpType type, int code) {
    IcmpMessage about = (IcmpMessage) original.payload();
    if (about.type().isError() || ingress.ip().isEmpty()) {
      return;
    }
    IcmpMessage error = new IcmpMessage(type, code, about.id(), about.seq());
    Ipv4Packet packet =
        Ipv4Packet.icmp(ingress.ip().get(), original.src(), sim.settings().routerTtl(), error);
    if (type == IcmpType.TIME_EXCEEDED) {
      sim.emit(
          router,
          null,
          3,
          SimAction.GENERATE,
          packet,
          ExplanationKey.ICMP_TIME_EXCEEDED_CREATED,
          original.src());
    } else {
      sim.emit(
          router,
          null,
          3,
          SimAction.GENERATE,
          packet,
          ExplanationKey.ICMP_UNREACHABLE_CREATED,
          original.src(),
          code);
    }
    originate(sim, router, packet);
  }
}
