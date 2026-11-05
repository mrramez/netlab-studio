package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.LinkDiagnosis;
import io.github.mrramez.netlabstudio.core.model.LinkState;
import io.github.mrramez.netlabstudio.core.model.Port;
import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import io.github.mrramez.netlabstudio.core.net.SubnetMask;
import java.util.Optional;

/** What a PC or server does: ping, the forwarding decision, ARP, answering echo requests. */
final class HostBehavior {

  private HostBehavior() {}

  /** The {@code ping} command sends one echo request. */
  static void startPing(
      Simulation sim, Host host, Ipv4Address destination, Simulation.Attempt attempt) {
    Port nic = host.nic();
    sim.emit(
        host,
        null,
        7,
        SimAction.GENERATE,
        null,
        ExplanationKey.PING_START,
        destination,
        attempt.sequence);
    if (!nic.hasIpv4()) {
      sim.emit(host, nic, 3, SimAction.DROP, null, ExplanationKey.HOST_NO_IP);
      sim.fail(FailureReason.NO_IP_ADDRESS, host);
      return;
    }
    Ipv4Address ownIp = nic.ip().orElseThrow();
    if (destination.equals(ownIp) || destination.isLoopback()) {
      // The packet never leaves the host: the protocol stack answers itself.
      sim.emit(host, null, 3, SimAction.DELIVER, null, ExplanationKey.PING_SELF, destination);
      sim.succeed(destination, sim.settings().hostTtl());
      return;
    }
    IcmpMessage echo = new IcmpMessage(IcmpType.ECHO_REQUEST, 0, attempt.id, attempt.sequence);
    Ipv4Packet packet = Ipv4Packet.icmp(ownIp, destination, sim.settings().hostTtl(), echo);
    sim.emit(
        host,
        null,
        3,
        SimAction.ENCAPSULATE,
        packet,
        ExplanationKey.ICMP_ECHO_REQUEST_CREATED,
        ownIp,
        destination,
        attempt.sequence,
        packet.ttl());
    send(sim, host, packet);
  }

  /**
   * The forwarding decision: is the destination on my network? If so, ARP for it; if not, ARP for
   * the default gateway. Then the packet goes out of the NIC if the link works.
   */
  static void send(Simulation sim, Host host, Ipv4Packet packet) {
    Port nic = host.nic();
    if (!nic.hasIpv4()) {
      sim.emit(host, nic, 3, SimAction.DROP, packet, ExplanationKey.HOST_NO_IP);
      sim.fail(FailureReason.NO_IP_ADDRESS, host);
      return;
    }
    Ipv4Address ownIp = nic.ip().orElseThrow();
    SubnetMask mask = nic.mask().orElseThrow();
    Ipv4Address ownNetwork = ownIp.and(mask);
    Ipv4Address destination = packet.dst();
    Ipv4Address destinationNetwork = destination.and(mask);

    Ipv4Address nextHop;
    if (ownNetwork.equals(destinationNetwork)) {
      sim.emit(
          host,
          null,
          3,
          SimAction.DECIDE,
          packet,
          ExplanationKey.HOST_DESTINATION_LOCAL,
          ownIp,
          mask,
          ownNetwork,
          destination,
          destinationNetwork);
      nextHop = destination;
    } else {
      Optional<Ipv4Address> gateway = host.defaultGateway();
      if (gateway.isEmpty()) {
        sim.emit(
            host,
            null,
            3,
            SimAction.DROP,
            packet,
            ExplanationKey.HOST_NO_GATEWAY,
            destination,
            ownNetwork);
        sim.fail(FailureReason.NO_DEFAULT_GATEWAY, host);
        return;
      }
      Ipv4Address gatewayNetwork = gateway.get().and(mask);
      if (!gatewayNetwork.equals(ownNetwork)) {
        sim.emit(
            host,
            null,
            3,
            SimAction.DROP,
            packet,
            ExplanationKey.HOST_GATEWAY_NOT_LOCAL,
            gateway.get(),
            ownIp,
            mask,
            ownNetwork,
            gatewayNetwork);
        sim.fail(FailureReason.GATEWAY_NOT_ON_LOCAL_SUBNET, host);
        return;
      }
      sim.emit(
          host,
          null,
          3,
          SimAction.DECIDE,
          packet,
          ExplanationKey.HOST_DESTINATION_REMOTE,
          ownIp,
          mask,
          ownNetwork,
          destination,
          destinationNetwork,
          gateway.get());
      nextHop = gateway.get();
    }

    LinkDiagnosis link = sim.topology().diagnose(nic);
    if (link.state() != LinkState.UP) {
      sim.emitKey(
          host,
          nic,
          1,
          SimAction.DROP,
          packet,
          link.explanation().key(),
          link.explanation().args().toArray());
      sim.fail(reasonOf(link.problem()), host);
      return;
    }
    sim.sendIpv4(host, nic, nextHop, packet, null, false, null);
  }

  /** A frame arrives at the NIC. */
  static void receive(Simulation sim, Host host, Port in, EthernetFrame frame) {
    sim.emit(
        host,
        in,
        2,
        SimAction.RECEIVE,
        frame,
        ExplanationKey.L2_FRAME_RECEIVED,
        in.name(),
        frame.srcMac(),
        frame.dstMac());
    MacAddress ownMac = in.mac().orElseThrow();
    if (frame.dstMac().isBroadcast()) {
      sim.emit(host, in, 2, SimAction.DECAPSULATE, frame, ExplanationKey.NIC_ACCEPTED_BROADCAST);
    } else if (frame.dstMac().equals(ownMac)) {
      sim.emit(
          host,
          in,
          2,
          SimAction.DECAPSULATE,
          frame,
          ExplanationKey.NIC_ACCEPTED_UNICAST,
          frame.dstMac());
    } else {
      // A NIC filters in hardware; on a hub this is what happens to every frame meant for others.
      sim.emit(
          host,
          in,
          2,
          SimAction.DROP,
          frame,
          ExplanationKey.NIC_FRAME_NOT_FOR_ME,
          frame.dstMac(),
          ownMac);
      return;
    }
    if (frame.payload() instanceof ArpMessage arp) {
      sim.handleArp(host, in, arp, false);
    } else if (frame.payload() instanceof Ipv4Packet packet) {
      receivePacket(sim, host, in, packet);
    }
  }

  private static void receivePacket(Simulation sim, Host host, Port in, Ipv4Packet packet) {
    Optional<Ipv4Address> ownIp = in.ip();
    if (ownIp.isEmpty() || !packet.dst().equals(ownIp.get())) {
      sim.emit(
          host,
          in,
          3,
          SimAction.DROP,
          packet,
          ExplanationKey.HOST_PACKET_NOT_FOR_ME,
          packet.dst(),
          ownIp.map(Ipv4Address::toString).orElse("none"));
      return;
    }
    IcmpMessage icmp = (IcmpMessage) packet.payload();
    switch (icmp.type()) {
      case ECHO_REQUEST -> {
        sim.emit(
            host,
            in,
            3,
            SimAction.DELIVER,
            packet,
            ExplanationKey.ICMP_ECHO_REQUEST_RECEIVED,
            packet.src(),
            icmp.seq());
        IcmpMessage echoReply = new IcmpMessage(IcmpType.ECHO_REPLY, 0, icmp.id(), icmp.seq());
        Ipv4Packet reply =
            Ipv4Packet.icmp(ownIp.get(), packet.src(), sim.settings().hostTtl(), echoReply);
        sim.emit(
            host,
            null,
            3,
            SimAction.GENERATE,
            reply,
            ExplanationKey.ICMP_ECHO_REPLY_CREATED,
            packet.src(),
            icmp.seq());
        send(sim, host, reply);
      }
      case ECHO_REPLY -> {
        if (sim.matchesAttempt(host, icmp)) {
          sim.emit(
              host,
              in,
              3,
              SimAction.DELIVER,
              packet,
              ExplanationKey.ICMP_ECHO_REPLY_RECEIVED,
              packet.src(),
              icmp.seq(),
              packet.ttl());
          sim.succeed(packet.src(), packet.ttl());
        } else {
          sim.emit(
              host, in, 3, SimAction.DROP, packet, ExplanationKey.ICMP_UNEXPECTED, packet.src());
        }
      }
      case DESTINATION_UNREACHABLE, TIME_EXCEEDED -> {
        if (sim.matchesAttempt(host, icmp)) {
          sim.emit(
              host,
              in,
              3,
              SimAction.DELIVER,
              packet,
              ExplanationKey.ICMP_ERROR_RECEIVED,
              packet.src(),
              icmp.type().name(),
              icmp.code());
          Simulation.Attempt attempt = sim.attempt();
          attempt.repliedFrom = packet.src();
          attempt.errorType = icmp.type();
          attempt.errorCode = icmp.code();
          // The router normally recorded the real cause already; this is the fallback.
          sim.fail(reasonOf(icmp), host);
        } else {
          sim.emit(
              host, in, 3, SimAction.DROP, packet, ExplanationKey.ICMP_UNEXPECTED, packet.src());
        }
      }
    }
  }

  private static FailureReason reasonOf(IcmpMessage icmp) {
    if (icmp.type() == IcmpType.TIME_EXCEEDED) {
      return FailureReason.TTL_EXPIRED;
    }
    return icmp.code() == 0 ? FailureReason.NO_ROUTE : FailureReason.HOST_UNREACHABLE;
  }

  static FailureReason reasonOf(LinkDiagnosis.Problem problem) {
    return switch (problem) {
      case NOT_CONNECTED -> FailureReason.NOT_CONNECTED;
      case WRONG_CABLE -> FailureReason.WRONG_CABLE;
      case ADMIN_DOWN_LOCAL, ADMIN_DOWN_PEER -> FailureReason.INTERFACE_DOWN;
      case NONE -> throw new IllegalArgumentException("a working link has no failure reason");
    };
  }
}
