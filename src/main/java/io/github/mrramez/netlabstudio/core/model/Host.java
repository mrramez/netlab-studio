package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.MacGenerator;
import io.github.mrramez.netlabstudio.core.net.SubnetMask;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A device that originates and terminates traffic: one network card (MAC, IPv4 address, mask) and a
 * default gateway. Also has an RS-232 port for a console cable.
 */
public abstract sealed class Host extends Device permits Pc, Server {

  /** The name of the network card. */
  public static final String NIC_NAME = "Fa0";

  /** The name of the serial port a console cable plugs into. */
  public static final String SERIAL_PORT_NAME = "RS232";

  private Ipv4Address defaultGateway;

  Host(String id, String name, DeviceType type, MacGenerator macs) {
    super(
        id,
        name,
        type,
        List.of(
            new PortSpec(NIC_NAME, PortType.FAST_ETHERNET, true, true),
            new PortSpec(SERIAL_PORT_NAME, PortType.RS232, false, true)),
        macs);
  }

  /** The network card. */
  public final Port nic() {
    return port(NIC_NAME);
  }

  /** The default gateway, if one is configured. */
  public final Optional<Ipv4Address> defaultGateway() {
    return Optional.ofNullable(defaultGateway);
  }

  /** Sets the default gateway. It is not checked against the NIC's subnet; the simulator does. */
  public final void setDefaultGateway(Ipv4Address gateway) {
    this.defaultGateway = Objects.requireNonNull(gateway, "gateway");
  }

  /** Removes the default gateway. */
  public final void clearDefaultGateway() {
    this.defaultGateway = null;
  }

  /** Configures the NIC and the default gateway in one step. */
  public final void configure(Ipv4Address ip, SubnetMask mask, Ipv4Address gateway) {
    nic().setIpv4(ip, mask);
    setDefaultGateway(gateway);
  }

  /** Configures the NIC without a default gateway. */
  public final void configure(Ipv4Address ip, SubnetMask mask) {
    nic().setIpv4(ip, mask);
    clearDefaultGateway();
  }
}
