package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.MacGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * A hub: a layer-1 multiport repeater. Its ports have no MAC address, and everything it hears on
 * one port it repeats on all the others, so all its ports share one collision domain.
 */
public final class Hub extends Device {

  /** The number of ports a new hub has. */
  public static final int DEFAULT_PORT_COUNT = 8;

  Hub(String id, String name, MacGenerator macs) {
    super(id, name, DeviceType.HUB, portSpecs(DEFAULT_PORT_COUNT), macs);
  }

  static List<PortSpec> portSpecs(int count) {
    List<PortSpec> specs = new ArrayList<>(count);
    for (int i = 1; i <= count; i++) {
      specs.add(new PortSpec("Port" + i, PortType.FAST_ETHERNET, false, true));
    }
    return specs;
  }
}
