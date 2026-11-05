package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.Objects;

/**
 * One row of an ARP cache ({@code arp -a}, {@code show ip arp}).
 *
 * @param ip the IP address
 * @param mac the MAC address it maps to
 * @param portName the port of the device the mapping was learned on
 */
public record ArpEntry(Ipv4Address ip, MacAddress mac, String portName) {

  /** Rejects missing parts. */
  public ArpEntry {
    Objects.requireNonNull(ip, "ip");
    Objects.requireNonNull(mac, "mac");
    Objects.requireNonNull(portName, "portName");
  }
}
