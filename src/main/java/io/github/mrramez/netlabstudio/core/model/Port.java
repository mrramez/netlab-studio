package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.Ipv4Network;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import io.github.mrramez.netlabstudio.core.net.SubnetMask;
import java.util.Objects;
import java.util.Optional;

/**
 * A port of a device: the NIC of a PC, a switch port or a router interface (Cisco calls the last
 * one an "interface", so the two words mean the same thing here).
 *
 * <p>A port holds configuration only. Whether its link is up is derived from the cable and from
 * both ends, see {@link #linkState()}. Not thread-safe; the model is edited from one thread.
 */
public final class Port {

  private final Device device;
  private final String name;
  private final PortType type;
  private final MacAddress mac;
  private boolean adminUp;
  private Ipv4Address ip;
  private SubnetMask mask;
  private Duplex duplex = Duplex.AUTO;
  private Link link;

  Port(Device device, String name, PortType type, MacAddress mac, boolean adminUp) {
    this.device = device;
    this.name = name;
    this.type = type;
    this.mac = mac;
    this.adminUp = adminUp;
  }

  /** The device this port belongs to. */
  public Device device() {
    return device;
  }

  /** The port name, e.g. {@code Fa0/1} or {@code G0/0}; unique within its device. */
  public String name() {
    return name;
  }

  /** A name that is unique in the whole topology: {@code deviceId/portName}. */
  public String id() {
    return device.id() + "/" + name;
  }

  /** A short label for messages, e.g. {@code R1 G0/0}. */
  public String label() {
    return device.name() + " " + name;
  }

  /** The connector type. */
  public PortType type() {
    return type;
  }

  /** The port's MAC address; empty for layer-1 devices (hubs, repeaters) and non-Ethernet ports. */
  public Optional<MacAddress> mac() {
    return Optional.ofNullable(mac);
  }

  /**
   * The pin-out of this Ethernet port.
   *
   * @throws IllegalStateException if the port is not an Ethernet port
   */
  public MdiType mdi() {
    if (!type.isEthernet()) {
      throw new IllegalStateException(label() + " is a " + type + " port and has no MDI/MDI-X");
    }
    return device.type().mdi();
  }

  /**
   * Whether the port is enabled by configuration. Router interfaces start disabled until {@code no
   * shutdown}; every other port starts enabled.
   */
  public boolean isAdminUp() {
    return adminUp;
  }

  /** Enables ({@code no shutdown}) or disables ({@code shutdown}) the port. */
  public void setAdminUp(boolean adminUp) {
    this.adminUp = adminUp;
  }

  /** The duplex setting. */
  public Duplex duplex() {
    return duplex;
  }

  /**
   * Sets the duplex.
   *
   * @throws IllegalStateException if this is not an Ethernet port
   */
  public void setDuplex(Duplex duplex) {
    Objects.requireNonNull(duplex, "duplex");
    if (!type.isEthernet()) {
      throw new IllegalStateException(label() + " is not an Ethernet port; it has no duplex");
    }
    this.duplex = duplex;
  }

  /** The IPv4 address, if one is configured. */
  public Optional<Ipv4Address> ip() {
    return Optional.ofNullable(ip);
  }

  /** The subnet mask, if an address is configured. */
  public Optional<SubnetMask> mask() {
    return Optional.ofNullable(mask);
  }

  /** Whether an IPv4 address and mask are configured. */
  public boolean hasIpv4() {
    return ip != null;
  }

  /** The network the configured address belongs to. */
  public Optional<Ipv4Network> network() {
    return hasIpv4() ? Optional.of(Ipv4Network.containing(ip, mask)) : Optional.empty();
  }

  /**
   * Configures the IPv4 address and mask.
   *
   * <p>Only PC and server NICs and router interfaces have an IP address. The address must be a
   * unicast host address: not multicast, reserved, loopback or {@code 0.0.0.0/8}, and for masks up
   * to /30 not the network or broadcast address of its own subnet (Cisco IOS refuses these too).
   *
   * @throws IllegalStateException if this port cannot hold an address
   * @throws IllegalArgumentException if the address is not usable as a host address
   */
  public void setIpv4(Ipv4Address ip, SubnetMask mask) {
    Objects.requireNonNull(ip, "ip");
    Objects.requireNonNull(mask, "mask");
    if (!canHaveIpv4()) {
      throw new IllegalStateException(label() + " cannot have an IP address");
    }
    if (ip.isMulticast()
        || ip.isReserved()
        || ip.isLimitedBroadcast()
        || ip.isLoopback()
        || ip.isThisNetwork()) {
      throw new IllegalArgumentException(ip + " is not a valid host address");
    }
    Ipv4Network network = Ipv4Network.containing(ip, mask);
    if (mask.prefixLength() <= 30
        && (ip.equals(network.network()) || ip.equals(network.broadcast()))) {
      throw new IllegalArgumentException(
          ip + mask.toCidrString() + " is the network or broadcast address, not a host address");
    }
    this.ip = ip;
    this.mask = mask;
  }

  /** Removes the IPv4 configuration ({@code no ip address}). */
  public void clearIpv4() {
    this.ip = null;
    this.mask = null;
  }

  /** Whether this port can hold an IPv4 address: PC/server NICs and router Ethernet/serial. */
  public boolean canHaveIpv4() {
    DeviceType deviceType = device.type();
    return (deviceType.isHost() && type.isEthernet())
        || (deviceType == DeviceType.ROUTER && (type.isEthernet() || type == PortType.SERIAL));
  }

  /** The link plugged into this port, if any. */
  public Optional<Link> link() {
    return Optional.ofNullable(link);
  }

  /** Whether a cable is plugged in. */
  public boolean isConnected() {
    return link != null;
  }

  /** The state of the link on this port; {@link LinkState#DOWN} when nothing is plugged in. */
  public LinkState linkState() {
    return link == null ? LinkState.DOWN : link.state();
  }

  /** Whether the link is {@link LinkState#UP}. */
  public boolean isLinkUp() {
    return linkState() == LinkState.UP;
  }

  void attach(Link link) {
    this.link = link;
  }

  void detach() {
    this.link = null;
  }

  @Override
  public String toString() {
    return id();
  }
}
