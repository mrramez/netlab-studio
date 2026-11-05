package io.github.mrramez.netlabstudio.core.sim;

/**
 * Every explanation key the simulator puts into a {@link SimEvent}, with the arguments that go with
 * it. This is the contract with the UI: each key needs an English and an Arabic message that uses
 * the arguments by position ({@code {0}}, {@code {1}}, ...). Addresses are passed as text and shown
 * left to right; names in capitals ({@code ECHO_REPLY}) are enum constant names.
 *
 * <p>Link problems are explained by {@code model.link.*} keys, see {@code LinkDiagnosis}.
 */
public enum ExplanationKey {

  // ---- ping (application layer) ----

  /** The ping command starts. Args: destination IP, sequence number. */
  PING_START("sim.ping.start"),
  /** The ping is answered. Args: reply source IP, sequence number, TTL of the reply, round trip. */
  PING_REPLY_RECEIVED("sim.ping.replyReceived"),
  /** No answer came. Args: destination IP, sequence number. */
  PING_TIMEOUT("sim.ping.timeout"),
  /** A router answered with an error. Args: router IP, sequence number, ICMP type, ICMP code. */
  PING_ERROR_REPLY("sim.ping.errorReply"),
  /** The destination is the host itself. Args: IP address. */
  PING_SELF("sim.ping.self"),

  // ---- host network layer ----

  /** The NIC has no IP address. Args: none. */
  HOST_NO_IP("sim.host.noIpAddress"),
  /**
   * The destination is on the same network, shown with the AND. Args: own IP, mask, own network
   * (own IP AND mask), destination IP, destination network (destination IP AND mask).
   */
  HOST_DESTINATION_LOCAL("sim.host.destinationLocal"),
  /**
   * The destination is on another network, so the packet goes to the gateway. Args: own IP, mask,
   * own network, destination IP, destination network, gateway IP.
   */
  HOST_DESTINATION_REMOTE("sim.host.destinationRemote"),
  /**
   * The destination is remote and there is no default gateway. Args: destination IP, own network.
   */
  HOST_NO_GATEWAY("sim.host.noDefaultGateway"),
  /**
   * The default gateway is not on the host's own network, so it cannot be reached directly. Args:
   * gateway IP, own IP, mask, own network, gateway network (gateway IP AND mask).
   */
  HOST_GATEWAY_NOT_LOCAL("sim.host.gatewayNotOnLocalSubnet"),
  /**
   * A packet reached a host that is not its destination; hosts do not forward. Args: destination
   * IP, own IP.
   */
  HOST_PACKET_NOT_FOR_ME("sim.host.packetNotForMe"),

  // ---- layers 1 and 2 on any device ----

  /** A hub or repeater heard a signal. Args: port name. */
  L1_SIGNAL_RECEIVED("sim.l1.signalReceived"),
  /** The cable on the egress port is down, so nothing was sent. Args: port name. */
  L1_NO_CARRIER("sim.l1.noCarrier"),
  /** A frame arrived. Args: port name, source MAC, destination MAC. */
  L2_FRAME_RECEIVED("sim.l2.frameReceived"),
  /** A frame left a port. Args: port name, destination MAC. */
  L2_FRAME_SENT("sim.l2.frameSent"),
  /** An IP packet was put into a new frame. Args: source MAC, destination MAC. */
  L2_ENCAPSULATED("sim.l2.encapsulated"),
  /** The frame is not addressed to this NIC and is ignored. Args: destination MAC, own MAC. */
  NIC_FRAME_NOT_FOR_ME("sim.nic.frameNotForMe"),
  /** The frame is addressed to this NIC. Args: destination MAC. */
  NIC_ACCEPTED_UNICAST("sim.nic.acceptedUnicast"),
  /** The frame is a broadcast, which every NIC accepts. Args: none. */
  NIC_ACCEPTED_BROADCAST("sim.nic.acceptedBroadcast"),

  // ---- ARP ----

  /** The next hop's MAC is in the ARP cache. Args: next-hop IP, its MAC. */
  ARP_CACHE_HIT("sim.arp.cacheHit"),
  /** The next hop's MAC is not known yet. Args: next-hop IP. */
  ARP_CACHE_MISS("sim.arp.cacheMiss"),
  /** An ARP request is broadcast. Args: IP being asked about, own IP. */
  ARP_REQUEST_SENT("sim.arp.requestSent"),
  /** An ARP request asks about this device's IP. Args: sender IP, sender MAC, target IP. */
  ARP_REQUEST_FOR_ME("sim.arp.requestForMe"),
  /** An ARP message is about someone else and is ignored. Args: target IP, own IP. */
  ARP_NOT_FOR_ME("sim.arp.notForMe"),
  /** An ARP reply is sent back to the requester. Args: own IP, own MAC, requester IP. */
  ARP_REPLY_SENT("sim.arp.replySent"),
  /** An ARP reply arrived. Args: IP, its MAC. */
  ARP_REPLY_RECEIVED("sim.arp.replyReceived"),
  /** An entry was added to the ARP cache. Args: IP, MAC. */
  ARP_CACHE_UPDATED("sim.arp.cacheUpdated"),
  /**
   * A host cannot send the packet yet, so it is dropped while ARP resolves; this is why the first
   * ping is lost. Args: destination IP, next-hop IP.
   */
  ARP_FIRST_PACKET_LOST("sim.arp.firstPacketLost"),
  /** A router holds the packet until ARP finishes. Args: destination IP, next-hop IP. */
  ARP_PACKET_HELD("sim.arp.packetHeld"),
  /** ARP finished and the held packet is sent. Args: destination IP, next-hop IP. */
  ARP_PACKET_RELEASED("sim.arp.packetReleased"),
  /** Nobody answered the ARP request. Args: IP that was asked about. */
  ARP_NO_REPLY("sim.arp.noReply"),
  /** A router gave up on a held packet because ARP got no answer. Args: destination IP. */
  ARP_HELD_PACKET_DISCARDED("sim.arp.heldPacketDiscarded"),

  // ---- hub / repeater ----

  /** The signal is regenerated on another port. Args: ingress port, egress port. */
  HUB_REPEAT("sim.hub.repeat"),

  // ---- bridge / switch ----

  /** A new source MAC was learned. Args: MAC, port. */
  SWITCH_LEARN_NEW("sim.switch.learnNew"),
  /** The source MAC was already known on this port. Args: MAC, port. */
  SWITCH_LEARN_REFRESH("sim.switch.learnKnown"),
  /** The source MAC moved to another port. Args: MAC, old port, new port. */
  SWITCH_LEARN_MOVED("sim.switch.learnMoved"),
  /** Broadcast (or multicast): flood. Args: destination MAC, ingress port. */
  SWITCH_FLOOD_BROADCAST("sim.switch.floodBroadcast"),
  /** Unknown unicast: flood. Args: destination MAC, ingress port. */
  SWITCH_FLOOD_UNKNOWN("sim.switch.floodUnknownUnicast"),
  /** Known unicast: forward to one port. Args: destination MAC, egress port. */
  SWITCH_FORWARD("sim.switch.forwardKnown"),
  /** The destination is on the port the frame came from: filter. Args: destination MAC, port. */
  SWITCH_FILTER("sim.switch.filterSameSegment"),

  // ---- router ----

  /** The interface has no IP address, so the router cannot handle the packet. Args: port name. */
  ROUTER_INTERFACE_NO_IP("sim.router.interfaceNoIp"),
  /** A broadcast is not forwarded: it stays in its broadcast domain. Args: none. */
  ROUTER_BROADCAST_NOT_FORWARDED("sim.router.broadcastNotForwarded"),
  /** The TTL was decremented. Args: old TTL, new TTL. */
  ROUTER_TTL_DECREMENT("sim.router.ttlDecrement"),
  /** The TTL reached zero and the packet is discarded. Args: source IP, destination IP. */
  ROUTER_TTL_EXPIRED("sim.router.ttlExpired"),
  /** A route matches. Args: destination IP, route network (CIDR), egress interface. */
  ROUTER_ROUTE_FOUND("sim.router.routeFound"),
  /** No route matches. Args: destination IP. */
  ROUTER_NO_ROUTE("sim.router.noRoute"),
  /**
   * No route, because the interface of the matching network is shut down: a connected route needs
   * the interface to be up/up, so {@code no shutdown} is missing. Args: destination IP, interface.
   */
  ROUTER_NO_ROUTE_INTERFACE_SHUTDOWN("sim.router.noRouteInterfaceShutdown"),
  /**
   * No route, because the matching interface has no working link. Args: destination IP, interface.
   */
  ROUTER_NO_ROUTE_INTERFACE_DOWN("sim.router.noRouteInterfaceDown"),
  /**
   * The router built a new frame for the next hop. Args: old source MAC, old destination MAC, new
   * source MAC, new destination MAC.
   */
  ROUTER_REWRITE_FRAME("sim.router.rewriteFrame"),
  /** The router has no route back to the sender of a packet it wanted to answer. Args: IP. */
  ROUTER_NO_ROUTE_TO_SENDER("sim.router.noRouteToSender"),

  // ---- ICMP ----

  /** An echo request was created. Args: source IP, destination IP, sequence number, TTL. */
  ICMP_ECHO_REQUEST_CREATED("sim.icmp.echoRequestCreated"),
  /** An echo request arrived. Args: source IP, sequence number. */
  ICMP_ECHO_REQUEST_RECEIVED("sim.icmp.echoRequestReceived"),
  /** An echo reply was created. Args: destination IP, sequence number. */
  ICMP_ECHO_REPLY_CREATED("sim.icmp.echoReplyCreated"),
  /** An echo reply arrived. Args: source IP, sequence number, TTL. */
  ICMP_ECHO_REPLY_RECEIVED("sim.icmp.echoReplyReceived"),
  /** A "time exceeded" message was created. Args: destination IP. */
  ICMP_TIME_EXCEEDED_CREATED("sim.icmp.timeExceededCreated"),
  /** A "destination unreachable" message was created. Args: destination IP, ICMP code. */
  ICMP_UNREACHABLE_CREATED("sim.icmp.unreachableCreated"),
  /** An ICMP error arrived. Args: source IP, ICMP type, ICMP code. */
  ICMP_ERROR_RECEIVED("sim.icmp.errorReceived"),
  /** An ICMP message that matches no ping in progress was ignored. Args: source IP. */
  ICMP_UNEXPECTED("sim.icmp.unexpected"),

  // ---- engine ----

  /**
   * The simulation stopped because it produced too many events, typically a loop of switches
   * flooding a broadcast. Args: the limit.
   */
  ENGINE_EVENT_LIMIT("sim.engine.eventLimit");

  private final String key;

  ExplanationKey(String key) {
    this.key = key;
  }

  /** The message key to look up in the message bundles. */
  public String key() {
    return key;
  }
}
