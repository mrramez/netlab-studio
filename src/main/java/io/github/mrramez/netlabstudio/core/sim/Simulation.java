package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.model.Bridge;
import io.github.mrramez.netlabstudio.core.model.Device;
import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Hub;
import io.github.mrramez.netlabstudio.core.model.Port;
import io.github.mrramez.netlabstudio.core.model.Repeater;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.model.Switch;
import io.github.mrramez.netlabstudio.core.model.Topology;
import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * One {@code ping} call: a virtual clock, a queue of frames in flight and the list of events.
 *
 * <p>This is the discrete-event core. A frame that leaves a port is scheduled to arrive at the port
 * on the other end of the cable one tick later; the queue hands out arrivals ordered by time and
 * then by the order they were scheduled, so a run is fully deterministic. The behaviour of each
 * kind of device lives in {@link HostBehavior}, {@link RepeaterBehavior}, {@link BridgeBehavior}
 * and {@link RouterBehavior}; this class provides what they share: emitting events, putting frames
 * on cables, ARP, and the bookkeeping of the ping in progress.
 *
 * <p>Pings are sent one after another: the next echo request starts only when nothing is left in
 * flight, which is what waiting for a reply or a timeout looks like on a virtual clock.
 */
final class Simulation {

  /** Thrown to unwind from deep inside a device's behaviour when the event limit is reached. */
  private static final class EventLimitReached extends RuntimeException {
    private static final long serialVersionUID = 1L;

    EventLimitReached() {
      super("event limit reached", null, false, false);
    }
  }

  /** A frame that will arrive at {@code port} at {@code time}. */
  private record Arrival(long time, long order, Port port, EthernetFrame frame) {}

  /** A packet waiting for ARP; {@code ingress} is where it came in (null if the device made it). */
  record Held(Ipv4Packet packet, EthernetFrame inbound, Port ingress) {}

  /** An ARP request that has not been answered yet. */
  static final class PendingArp {
    final Device device;
    final Port out;
    final Ipv4Address ip;
    final List<Held> held = new ArrayList<>();

    PendingArp(Device device, Port out, Ipv4Address ip) {
      this.device = device;
      this.out = out;
      this.ip = ip;
    }
  }

  /** The echo request in progress and what has become of it so far. */
  static final class Attempt {
    final int sequence;
    final int id;
    final Host source;
    final long startedAt;
    boolean success;
    FailureReason failure;
    String failedAtDeviceId;
    Ipv4Address repliedFrom;
    int replyTtl;
    long roundTripTicks;
    IcmpType errorType;
    int errorCode;

    Attempt(int sequence, int id, Host source, long startedAt) {
      this.sequence = sequence;
      this.id = id;
      this.source = source;
      this.startedAt = startedAt;
    }
  }

  private final Topology topology;
  private final SimulationSettings settings;
  private final Map<String, DeviceState> states;
  private final PriorityQueue<Arrival> queue =
      new PriorityQueue<>(
          Comparator.comparingLong(Arrival::time).thenComparingLong(Arrival::order));
  private final List<SimEvent> events = new ArrayList<>();
  private final Map<String, Map<Ipv4Address, PendingArp>> pending = new LinkedHashMap<>();
  private long clock;
  private long nextOrder;
  private Attempt attempt;

  Simulation(Topology topology, SimulationSettings settings, Map<String, DeviceState> states) {
    this.topology = topology;
    this.settings = settings;
    this.states = states;
  }

  Topology topology() {
    return topology;
  }

  SimulationSettings settings() {
    return settings;
  }

  long now() {
    return clock;
  }

  Attempt attempt() {
    return attempt;
  }

  DeviceState state(Device device) {
    return states.computeIfAbsent(device.id(), id -> new DeviceState());
  }

  // ---- running a ping ---------------------------------------------------------------------

  PingResult ping(Host source, Ipv4Address destination, int count, int icmpId) {
    List<PingAttempt> results = new ArrayList<>();
    boolean limitReached = false;
    int sequence = 1;
    try {
      for (; sequence <= count; sequence++) {
        results.add(runAttempt(source, destination, icmpId, sequence));
      }
    } catch (EventLimitReached e) {
      limitReached = true;
      // The limit event bypasses the cap, so the list ends with an explanation of why it stopped.
      events.add(
          new SimEvent(
              clock,
              source.id(),
              null,
              3,
              SimAction.DROP,
              null,
              ExplanationKey.ENGINE_EVENT_LIMIT.key(),
              List.of(settings.maxEvents())));
      for (; sequence <= count; sequence++) {
        results.add(
            new PingAttempt(sequence, FailureReason.EVENT_LIMIT_EXCEEDED, source.id(), null, 0));
      }
    }
    return new PingResult(source.id(), destination, results, events, limitReached);
  }

  /**
   * Puts a frame on a port as if it had just arrived there and runs until nothing is left in
   * flight. A ping can only produce well-formed traffic; this lets tests (and later features such
   * as a "send custom frame" tool) feed a device frames that it must still handle sensibly.
   */
  List<SimEvent> inject(Port in, EthernetFrame frame) {
    queue.add(new Arrival(clock, nextOrder++, in, frame));
    settle();
    return List.copyOf(events);
  }

  private PingAttempt runAttempt(Host source, Ipv4Address destination, int icmpId, int sequence) {
    Attempt current = new Attempt(sequence, icmpId, source, clock);
    attempt = current;
    HostBehavior.startPing(this, source, destination, current);
    settle();

    if (current.success) {
      emit(
          source,
          null,
          7,
          SimAction.DELIVER,
          null,
          ExplanationKey.PING_REPLY_RECEIVED,
          current.repliedFrom,
          sequence,
          current.replyTtl,
          current.roundTripTicks);
    } else {
      if (current.failure == null) {
        current.failure = FailureReason.NO_REPLY;
        current.failedAtDeviceId = source.id();
      }
      if (current.repliedFrom != null) {
        // A router reported an error: the user sees "Reply from <router>: ...".
        emit(
            source,
            null,
            7,
            SimAction.DELIVER,
            null,
            ExplanationKey.PING_ERROR_REPLY,
            current.repliedFrom,
            sequence,
            current.errorType.name(),
            current.errorCode);
      } else {
        clock++; // waiting for the timeout takes time
        emit(
            source,
            null,
            7,
            SimAction.TIMEOUT,
            null,
            ExplanationKey.PING_TIMEOUT,
            destination,
            sequence);
      }
    }
    clock++;
    attempt = null;
    return current.success
        ? new PingAttempt(sequence, null, null, current.repliedFrom, current.roundTripTicks)
        : new PingAttempt(
            sequence, current.failure, current.failedAtDeviceId, current.repliedFrom, 0);
  }

  /** Runs until nothing is in flight and no ARP request is waiting for an answer. */
  private void settle() {
    boolean timedOut;
    do {
      drainQueue();
      timedOut = expirePendingArps();
    } while (timedOut || !queue.isEmpty());
  }

  private void drainQueue() {
    Arrival arrival;
    while ((arrival = queue.poll()) != null) {
      clock = arrival.time();
      deliver(arrival.port(), arrival.frame());
    }
  }

  private void deliver(Port in, EthernetFrame frame) {
    switch (in.device()) {
      case Host host -> HostBehavior.receive(this, host, in, frame);
      case Hub hub -> RepeaterBehavior.receive(this, hub, in, frame);
      case Repeater repeater -> RepeaterBehavior.receive(this, repeater, in, frame);
      case Bridge bridge -> BridgeBehavior.receive(this, bridge, in, frame);
      case Switch device -> BridgeBehavior.receive(this, device, in, frame);
      case Router router -> RouterBehavior.receive(this, router, in, frame);
    }
  }

  // ---- events and cables ------------------------------------------------------------------

  /**
   * Records a step. Arguments that are not already text, {@link Integer}, {@link Long} or {@link
   * Boolean} (addresses, masks, networks, enums) are turned into text with {@code toString()}.
   */
  void emit(
      Device device,
      Port port,
      int layer,
      SimAction action,
      Pdu pdu,
      ExplanationKey key,
      Object... args) {
    emitKey(device, port, layer, action, pdu, key.key(), args);
  }

  /** Like {@link #emit} but with a key that is not one of the simulator's own (link messages). */
  void emitKey(
      Device device, Port port, int layer, SimAction action, Pdu pdu, String key, Object... args) {
    if (events.size() >= settings.maxEvents()) {
      throw new EventLimitReached();
    }
    List<Object> values = new ArrayList<>(args.length);
    for (Object arg : args) {
      boolean plain =
          arg instanceof String
              || arg instanceof Integer
              || arg instanceof Long
              || arg instanceof Boolean;
      values.add(plain ? arg : String.valueOf(arg));
    }
    events.add(
        new SimEvent(
            clock,
            device.id(),
            port == null ? null : port.name(),
            layer,
            action,
            pdu,
            key,
            values));
  }

  /**
   * Puts a frame on the cable of {@code out}; it arrives one tick later at the other end. If the
   * link cannot carry frames, nothing is sent and the step is recorded as a drop.
   */
  void transmit(
      Port out,
      EthernetFrame frame,
      SimAction action,
      int layer,
      ExplanationKey key,
      Object... args) {
    Optional<Port> peer = topology.livePeer(out);
    if (peer.isEmpty()) {
      emit(out.device(), out, 1, SimAction.DROP, frame, ExplanationKey.L1_NO_CARRIER, out.name());
      return;
    }
    emit(out.device(), out, layer, action, frame, key, args);
    queue.add(new Arrival(clock + 1, nextOrder++, peer.get(), frame));
  }

  // ---- ping bookkeeping -------------------------------------------------------------------

  /** Records why the current ping failed, unless a cause was already found. */
  void fail(FailureReason reason, Device at) {
    if (attempt != null && attempt.failure == null) {
      attempt.failure = reason;
      attempt.failedAtDeviceId = at.id();
    }
  }

  void succeed(Ipv4Address from, int ttl) {
    attempt.success = true;
    attempt.repliedFrom = from;
    attempt.replyTtl = ttl;
    attempt.roundTripTicks = clock - attempt.startedAt;
  }

  /** Whether an ICMP message belongs to the ping in progress. */
  boolean matchesAttempt(Host host, IcmpMessage icmp) {
    return attempt != null
        && attempt.source == host
        && attempt.id == icmp.id()
        && attempt.sequence == icmp.seq();
  }

  // ---- ARP and IPv4 output (shared by hosts and routers) ----------------------------------

  /**
   * Sends an IPv4 packet to the next hop through {@code out}: finds the next hop's MAC address in
   * the ARP cache, or asks for it with ARP first.
   *
   * @param inbound the frame the packet arrived in if a router is forwarding it (its addresses are
   *     replaced), or {@code null} if this device created the packet
   * @param dropWhileResolving whether to drop the packet when ARP is needed (Cisco IOS routers do,
   *     which is why the first ping through a router is lost) instead of holding it until the reply
   *     arrives (hosts always do)
   * @param ingress the port a forwarded packet came in on, or {@code null}
   */
  void sendIpv4(
      Device device,
      Port out,
      Ipv4Address nextHop,
      Ipv4Packet packet,
      EthernetFrame inbound,
      boolean dropWhileResolving,
      Port ingress) {
    ArpEntry known = state(device).arp.get(nextHop);
    if (known != null) {
      emit(
          device,
          out,
          2,
          SimAction.LOOKUP,
          packet,
          ExplanationKey.ARP_CACHE_HIT,
          nextHop,
          known.mac());
      sendFrame(device, out, known.mac(), packet, inbound);
      return;
    }
    emit(device, out, 2, SimAction.LOOKUP, packet, ExplanationKey.ARP_CACHE_MISS, nextHop);
    Map<Ipv4Address, PendingArp> waiting =
        pending.computeIfAbsent(device.id(), id -> new LinkedHashMap<>());
    PendingArp request = waiting.get(nextHop);
    boolean firstRequest = request == null;
    if (firstRequest) {
      request = new PendingArp(device, out, nextHop);
      waiting.put(nextHop, request);
    }
    if (dropWhileResolving) {
      emit(
          device,
          out,
          3,
          SimAction.DROP,
          packet,
          ExplanationKey.ROUTER_ARP_PACKET_DROPPED,
          packet.dst(),
          nextHop);
      fail(FailureReason.LOST_TO_ARP, device);
    } else {
      request.held.add(new Held(packet, inbound, ingress));
      emit(
          device,
          out,
          2,
          SimAction.QUEUE,
          packet,
          ExplanationKey.ARP_PACKET_HELD,
          packet.dst(),
          nextHop);
    }
    if (firstRequest) {
      sendArpRequest(device, out, nextHop);
    }
  }

  private void sendFrame(
      Device device, Port out, MacAddress dstMac, Ipv4Packet packet, EthernetFrame inbound) {
    MacAddress srcMac = out.mac().orElseThrow();
    EthernetFrame frame = EthernetFrame.ipv4(srcMac, dstMac, packet);
    if (inbound == null) {
      emit(
          device,
          out,
          2,
          SimAction.ENCAPSULATE,
          frame,
          ExplanationKey.L2_ENCAPSULATED,
          srcMac,
          dstMac);
    } else {
      emit(
          device,
          out,
          2,
          SimAction.REWRITE,
          frame,
          ExplanationKey.ROUTER_REWRITE_FRAME,
          inbound.srcMac(),
          inbound.dstMac(),
          srcMac,
          dstMac);
    }
    transmit(out, frame, SimAction.TRANSMIT, 2, ExplanationKey.L2_FRAME_SENT, out.name(), dstMac);
  }

  private void sendArpRequest(Device device, Port out, Ipv4Address target) {
    MacAddress mac = out.mac().orElseThrow();
    Ipv4Address ownIp = out.ip().orElseThrow();
    ArpMessage request = ArpMessage.request(mac, ownIp, target);
    emit(
        device,
        out,
        2,
        SimAction.GENERATE,
        request,
        ExplanationKey.ARP_REQUEST_SENT,
        target,
        ownIp);
    transmit(
        out,
        EthernetFrame.arp(mac, MacAddress.BROADCAST, request),
        SimAction.TRANSMIT,
        2,
        ExplanationKey.L2_FRAME_SENT,
        out.name(),
        MacAddress.BROADCAST);
  }

  /**
   * Handles an ARP message that a host or router accepted on {@code in}: answers a request for its
   * own address, learns from requests and replies, and sends packets that were waiting.
   */
  void handleArp(Device device, Port in, ArpMessage arp, boolean router) {
    Ipv4Address ownIp = in.ip().orElse(null);
    boolean forMe = ownIp != null && arp.targetIp().equals(ownIp);
    if (!forMe) {
      if (router && arp.op() == ArpOperation.REQUEST) {
        emit(device, in, 2, SimAction.DROP, arp, ExplanationKey.ROUTER_BROADCAST_NOT_FORWARDED);
      } else {
        emit(
            device,
            in,
            2,
            SimAction.DROP,
            arp,
            ExplanationKey.ARP_NOT_FOR_ME,
            arp.targetIp(),
            ownIp == null ? "none" : ownIp.toString());
      }
      return;
    }
    if (arp.op() == ArpOperation.REQUEST) {
      emit(
          device,
          in,
          2,
          SimAction.DELIVER,
          arp,
          ExplanationKey.ARP_REQUEST_FOR_ME,
          arp.senderIp(),
          arp.senderMac(),
          arp.targetIp());
      learn(device, in, arp);
      MacAddress ownMac = in.mac().orElseThrow();
      ArpMessage reply = ArpMessage.reply(ownMac, ownIp, arp.senderMac(), arp.senderIp());
      emit(
          device,
          in,
          2,
          SimAction.GENERATE,
          reply,
          ExplanationKey.ARP_REPLY_SENT,
          ownIp,
          ownMac,
          arp.senderIp());
      transmit(
          in,
          EthernetFrame.arp(ownMac, arp.senderMac(), reply),
          SimAction.TRANSMIT,
          2,
          ExplanationKey.L2_FRAME_SENT,
          in.name(),
          arp.senderMac());
    } else {
      emit(
          device,
          in,
          2,
          SimAction.DELIVER,
          arp,
          ExplanationKey.ARP_REPLY_RECEIVED,
          arp.senderIp(),
          arp.senderMac());
      learn(device, in, arp);
    }
  }

  /** Adds the sender of an ARP message to the cache and sends what was waiting for it. */
  private void learn(Device device, Port in, ArpMessage arp) {
    Ipv4Address ip = arp.senderIp();
    MacAddress mac = arp.senderMac();
    state(device).arp.put(ip, new ArpEntry(ip, mac, in.name()));
    emit(device, in, 2, SimAction.CACHE_UPDATE, arp, ExplanationKey.ARP_CACHE_UPDATED, ip, mac);

    Map<Ipv4Address, PendingArp> waiting = pending.get(device.id());
    PendingArp request = waiting == null ? null : waiting.remove(ip);
    if (request == null) {
      return;
    }
    for (Held held : request.held) {
      emit(
          device,
          request.out,
          2,
          SimAction.RELEASE,
          held.packet(),
          ExplanationKey.ARP_PACKET_RELEASED,
          held.packet().dst(),
          ip);
      sendFrame(device, request.out, mac, held.packet(), held.inbound());
    }
  }

  /**
   * Called when nothing is in flight: every ARP request still unanswered has timed out. The device
   * throws away the packets it held, the ping records that the next hop is unreachable, and a
   * router also tells the packets' senders with an ICMP "host unreachable".
   *
   * @return whether there were any, i.e. whether new events may have been scheduled
   */
  private boolean expirePendingArps() {
    List<PendingArp> expired = new ArrayList<>();
    for (Map<Ipv4Address, PendingArp> byIp : pending.values()) {
      expired.addAll(byIp.values());
    }
    pending.clear();
    if (expired.isEmpty()) {
      return false;
    }
    clock++; // waiting for the answer takes time
    for (PendingArp request : expired) {
      emit(
          request.device,
          request.out,
          2,
          SimAction.TIMEOUT,
          null,
          ExplanationKey.ARP_NO_REPLY,
          request.ip);
      if (attempt != null && attempt.failure == FailureReason.LOST_TO_ARP) {
        // The first packet was lost because ARP never finished; "host unreachable" is the cause.
        attempt.failure = null;
      }
      fail(FailureReason.HOST_UNREACHABLE, request.device);
      for (Held held : request.held) {
        emit(
            request.device,
            request.out,
            3,
            SimAction.DROP,
            held.packet(),
            ExplanationKey.ARP_HELD_PACKET_DISCARDED,
            held.packet().dst());
        if (request.device instanceof Router router && held.ingress() != null) {
          RouterBehavior.sendError(
              this, router, held.ingress(), held.packet(), IcmpType.DESTINATION_UNREACHABLE, 1);
        }
      }
    }
    return true;
  }
}
