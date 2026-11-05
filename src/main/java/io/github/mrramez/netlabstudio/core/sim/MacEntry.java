package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.Objects;

/**
 * One row of a switch's MAC address table ({@code show mac address-table}).
 *
 * @param mac a learned source MAC address
 * @param portName the port it was last seen on
 */
public record MacEntry(MacAddress mac, String portName) {

  /** Rejects missing parts. */
  public MacEntry {
    Objects.requireNonNull(mac, "mac");
    Objects.requireNonNull(portName, "portName");
  }
}
