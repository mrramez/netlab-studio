package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.model.Device;
import io.github.mrramez.netlabstudio.core.model.DeviceType;
import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.model.Topology;
import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs traffic through a {@link Topology} and records every step.
 *
 * <p>The engine is a deterministic discrete-event simulation: a virtual clock counts ticks (a frame
 * needs one tick to cross a cable), frames in flight wait in an event queue ordered by time and
 * then by the order they were sent, and nothing reads the wall clock or a random number generator.
 * The same topology and the same calls always produce the same events, which is what lets tests
 * assert exact sequences and lets the UI replay a run step by step.
 *
 * <p>The engine remembers what devices learned: ARP caches of hosts and routers, MAC tables of
 * switches and bridges. They persist from one {@link #ping} to the next (so the second ping needs
 * no ARP) until cleared. Each ping call starts its own clock at 0. Every call is capped at {@link
 * SimulationSettings#maxEvents()} events, so a loop of switches ends as an explained failure
 * instead of a hang.
 *
 * <p>The topology is read live and may be edited between calls (change an IP address, shut an
 * interface); cached tables are not touched by edits. Not thread-safe.
 */
public final class SimulationEngine {

  /** The most echo requests one {@link #ping} call may send. */
  public static final int MAX_PING_COUNT = 50;

  private final Topology topology;
  private final SimulationSettings settings;
  private final Map<String, DeviceState> states = new HashMap<>();
  private int lastIcmpId;

  /** Creates an engine for a topology with the default settings. */
  public SimulationEngine(Topology topology) {
    this(topology, SimulationSettings.defaults());
  }

  /** Creates an engine for a topology. */
  public SimulationEngine(Topology topology, SimulationSettings settings) {
    this.topology = Objects.requireNonNull(topology, "topology");
    this.settings = Objects.requireNonNull(settings, "settings");
  }

  /** The topology this engine simulates. */
  public Topology topology() {
    return topology;
  }

  /** The settings. */
  public SimulationSettings settings() {
    return settings;
  }

  /**
   * Pings {@code destination} from a PC or server: sends {@code count} ICMP echo requests, one
   * after the other, each waiting for its answer or its timeout.
   *
   * @param sourceDeviceId the id of a PC or server
   * @param destination the address to ping
   * @param count how many echo requests to send, 1 to {@value #MAX_PING_COUNT}
   * @return what became of each request and the events to replay
   * @throws IllegalArgumentException if the device does not exist, is not a PC or server, or the
   *     count is out of range
   */
  public PingResult ping(String sourceDeviceId, Ipv4Address destination, int count) {
    Objects.requireNonNull(destination, "destination");
    Device device = topology.device(Objects.requireNonNull(sourceDeviceId, "sourceDeviceId"));
    if (!(device instanceof Host host)) {
      throw new IllegalArgumentException(
          device.name() + " is a " + device.type() + "; only a PC or server can run ping");
    }
    if (count < 1 || count > MAX_PING_COUNT) {
      throw new IllegalArgumentException(
          "count must be between 1 and " + MAX_PING_COUNT + ", but was " + count);
    }
    lastIcmpId = lastIcmpId % 0xFFFF + 1;
    return newSimulation().ping(host, destination, count, lastIcmpId);
  }

  /**
   * The MAC address table of a switch or bridge, in the order entries were learned.
   *
   * @throws IllegalArgumentException if the device is not a switch or bridge
   */
  public List<MacEntry> macTable(String deviceId) {
    Device device = topology.device(deviceId);
    if (device.type() != DeviceType.SWITCH && device.type() != DeviceType.BRIDGE) {
      throw new IllegalArgumentException(device.name() + " has no MAC address table");
    }
    List<MacEntry> entries = new ArrayList<>();
    stateOf(device).macTable.forEach((mac, port) -> entries.add(new MacEntry(mac, port)));
    return List.copyOf(entries);
  }

  /**
   * The ARP cache of a PC, server or router, in the order entries were learned.
   *
   * @throws IllegalArgumentException if the device has no ARP cache
   */
  public List<ArpEntry> arpTable(String deviceId) {
    Device device = topology.device(deviceId);
    if (!(device instanceof Host) && !(device instanceof Router)) {
      throw new IllegalArgumentException(device.name() + " has no ARP table");
    }
    return List.copyOf(stateOf(device).arp.values());
  }

  /**
   * The routing table of a router: its connected routes, which exist while an interface has an
   * address and is up/up. Computed from the topology as it is now.
   *
   * @throws IllegalArgumentException if the device is not a router
   */
  public List<Route> routingTable(String deviceId) {
    if (!(topology.device(deviceId) instanceof Router router)) {
      throw new IllegalArgumentException(topology.device(deviceId).name() + " is not a router");
    }
    return List.copyOf(RoutingTables.connected(router));
  }

  /** The collision and broadcast domains of the topology as it is now. */
  public NetworkDomains domains() {
    return NetworkDomains.analyze(topology);
  }

  /** Empties the ARP cache of a host or router ({@code arp -d *}, {@code clear arp-cache}). */
  public void clearArpTable(String deviceId) {
    stateOf(topology.device(deviceId)).arp.clear();
  }

  /** Empties the MAC address table of a switch or bridge. */
  public void clearMacTable(String deviceId) {
    stateOf(topology.device(deviceId)).macTable.clear();
  }

  /** Forgets everything the devices learned, as after a power cycle. */
  public void resetState() {
    states.clear();
  }

  /** A fresh run that shares this engine's learned tables. */
  Simulation newSimulation() {
    return new Simulation(topology, settings, states);
  }

  private DeviceState stateOf(Device device) {
    return states.computeIfAbsent(device.id(), id -> new DeviceState());
  }
}
