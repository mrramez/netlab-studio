package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.MacGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A network: devices and the cables between their ports.
 *
 * <p>The topology owns the seeded {@link MacGenerator}, so the same seed and the same order of
 * {@code add...} calls always give the same MAC addresses. Devices and links keep the order in
 * which they were added, which makes every listing (and therefore every simulation) deterministic.
 * Sizes are capped so a damaged save file cannot ask for unbounded memory. Not thread-safe.
 */
public final class Topology {

  /** The MAC seed used when none is given. */
  public static final long DEFAULT_MAC_SEED = 2026L;

  /** The most devices a topology may hold. */
  public static final int MAX_DEVICES = 500;

  /** The most links a topology may hold. */
  public static final int MAX_LINKS = 2_000;

  private final MacGenerator macs;
  private final Map<String, Device> devices = new LinkedHashMap<>();
  private final List<Link> links = new ArrayList<>();
  private CableRules cableRules = CableRules.strictCabling();
  private int nextDeviceNumber = 1;
  private int nextLinkNumber = 1;

  /** Creates an empty topology with the default MAC seed and strict cabling. */
  public Topology() {
    this(DEFAULT_MAC_SEED);
  }

  /** Creates an empty topology whose MAC addresses follow from {@code macSeed}. */
  public Topology(long macSeed) {
    this.macs = new MacGenerator(macSeed);
  }

  /** The cabling rules (strict by default). */
  public CableRules cableRules() {
    return cableRules;
  }

  /** Switches between strict cabling and auto-MDIX; all link states follow at once. */
  public void setCableRules(CableRules cableRules) {
    this.cableRules = Objects.requireNonNull(cableRules, "cableRules");
  }

  // ---- devices ----------------------------------------------------------------------------

  /**
   * Adds a device of the given type with a generated id and its default ports.
   *
   * @throws IllegalStateException if the topology is full
   */
  public Device addDevice(DeviceType type, String name) {
    return addDevice(type, generateDeviceId(), name);
  }

  /**
   * Adds a device with an explicit id (for loading saved topologies).
   *
   * @throws IllegalArgumentException if the id is already used or not valid
   * @throws IllegalStateException if the topology is full
   */
  public Device addDevice(DeviceType type, String id, String name) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(id, "id");
    if (devices.size() >= MAX_DEVICES) {
      throw new IllegalStateException("a topology holds at most " + MAX_DEVICES + " devices");
    }
    if (devices.containsKey(id)) {
      throw new IllegalArgumentException("there is already a device with id '" + id + "'");
    }
    Device device =
        switch (type) {
          case PC -> new Pc(id, name, macs);
          case SERVER -> new Server(id, name, macs);
          case HUB -> new Hub(id, name, macs);
          case REPEATER -> new Repeater(id, name, macs);
          case BRIDGE -> new Bridge(id, name, macs);
          case SWITCH -> new Switch(id, name, macs);
          case ROUTER -> new Router(id, name, macs, Router.DEFAULT_GIGABIT_COUNT, 0);
        };
    devices.put(id, device);
    return device;
  }

  /** Adds a PC. */
  public Pc addPc(String name) {
    return (Pc) addDevice(DeviceType.PC, name);
  }

  /** Adds a server. */
  public Server addServer(String name) {
    return (Server) addDevice(DeviceType.SERVER, name);
  }

  /** Adds a hub. */
  public Hub addHub(String name) {
    return (Hub) addDevice(DeviceType.HUB, name);
  }

  /** Adds a repeater. */
  public Repeater addRepeater(String name) {
    return (Repeater) addDevice(DeviceType.REPEATER, name);
  }

  /** Adds a bridge. */
  public Bridge addBridge(String name) {
    return (Bridge) addDevice(DeviceType.BRIDGE, name);
  }

  /** Adds a switch. */
  public Switch addSwitch(String name) {
    return (Switch) addDevice(DeviceType.SWITCH, name);
  }

  /** Adds a router with two Gigabit Ethernet interfaces ({@code G0/0}, {@code G0/1}). */
  public Router addRouter(String name) {
    return (Router) addDevice(DeviceType.ROUTER, name);
  }

  /**
   * Adds a router with the given interfaces.
   *
   * @param gigabit number of Gigabit Ethernet interfaces, 1 to 8
   * @param serial number of serial interfaces, 0 to 4
   */
  public Router addRouter(String name, int gigabit, int serial) {
    if (devices.size() >= MAX_DEVICES) {
      throw new IllegalStateException("a topology holds at most " + MAX_DEVICES + " devices");
    }
    Router router = new Router(generateDeviceId(), name, macs, gigabit, serial);
    devices.put(router.id(), router);
    return router;
  }

  /** All devices in the order they were added. The list is a snapshot. */
  public List<Device> devices() {
    return List.copyOf(devices.values());
  }

  /** The device with this id, if there is one. */
  public Optional<Device> findDevice(String id) {
    return Optional.ofNullable(devices.get(id));
  }

  /**
   * The device with this id.
   *
   * @throws IllegalArgumentException if there is none
   */
  public Device device(String id) {
    return findDevice(id)
        .orElseThrow(() -> new IllegalArgumentException("no device with id '" + id + "'"));
  }

  /** Removes a device and every link attached to it. */
  public void removeDevice(String id) {
    Device device = device(id);
    for (Port port : device.ports()) {
      port.link().ifPresent(this::disconnect);
    }
    devices.remove(id);
  }

  // ---- links ------------------------------------------------------------------------------

  /**
   * Plugs a cable between two free ports.
   *
   * <p>A cable that cannot work between these ports (a straight-through cable between two PCs in
   * strict mode, say) is accepted: the link is created but stays {@link LinkState#DOWN}, so
   * students can see what a wrong cable does. See {@link #diagnose(Port)} for the reason.
   *
   * @throws IllegalArgumentException if a port is not in this topology, the ports are the same, or
   *     a port already has a cable
   * @throws IllegalStateException if the topology is full
   */
  public Link connect(Port a, Port b, CableType cable) {
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(b, "b");
    Objects.requireNonNull(cable, "cable");
    requireOwned(a);
    requireOwned(b);
    if (a == b) {
      throw new IllegalArgumentException("a cable needs two different ports");
    }
    for (Port port : List.of(a, b)) {
      if (port.isConnected()) {
        throw new IllegalArgumentException(port.label() + " already has a cable");
      }
    }
    if (links.size() >= MAX_LINKS) {
      throw new IllegalStateException("a topology holds at most " + MAX_LINKS + " links");
    }
    Link link = new Link(this, "L" + nextLinkNumber++, a, b, cable);
    a.attach(link);
    b.attach(link);
    links.add(link);
    return link;
  }

  /** Plugs a cable between the named ports of two devices. */
  public Link connect(Device a, String portA, Device b, String portB, CableType cable) {
    return connect(a.port(portA), b.port(portB), cable);
  }

  /** Unplugs a cable. */
  public void disconnect(Link link) {
    Objects.requireNonNull(link, "link");
    if (!links.remove(link)) {
      throw new IllegalArgumentException("link " + link.id() + " is not in this topology");
    }
    link.portA().detach();
    link.portB().detach();
    link.detach();
  }

  /** All links in the order they were added. The list is a snapshot. */
  public List<Link> links() {
    return Collections.unmodifiableList(new ArrayList<>(links));
  }

  // ---- link state -------------------------------------------------------------------------

  /**
   * Why the link on {@code port} is up or down, with an explanation. A port with no cable is {@link
   * LinkState#DOWN}.
   */
  public LinkDiagnosis diagnose(Port port) {
    requireOwned(Objects.requireNonNull(port, "port"));
    return LinkDiagnosis.of(port, cableRules);
  }

  /**
   * The port at the other end of the cable, if the link is up and can carry Ethernet frames. This
   * is the only way the simulator moves a frame from one port to another.
   */
  public Optional<Port> livePeer(Port port) {
    return port.link().filter(Link::carriesFrames).map(link -> link.other(port));
  }

  private void requireOwned(Port port) {
    if (devices.get(port.device().id()) != port.device()) {
      throw new IllegalArgumentException(port.label() + " is not part of this topology");
    }
  }

  private String generateDeviceId() {
    String id = "d" + nextDeviceNumber++;
    while (devices.containsKey(id)) {
      id = "d" + nextDeviceNumber++;
    }
    return id;
  }
}
