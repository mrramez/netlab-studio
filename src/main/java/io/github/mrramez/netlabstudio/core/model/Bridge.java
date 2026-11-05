package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.MacGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * A bridge: a layer-2 device that learns which MAC address lives behind which port and forwards or
 * filters frames accordingly. It has few ports and each port is its own collision domain.
 */
public final class Bridge extends Device {

  /** The number of ports a new bridge has. */
  public static final int DEFAULT_PORT_COUNT = 4;

  Bridge(String id, String name, MacGenerator macs) {
    super(id, name, DeviceType.BRIDGE, portSpecs(), macs);
  }

  private static List<PortSpec> portSpecs() {
    List<PortSpec> specs = new ArrayList<>(DEFAULT_PORT_COUNT);
    for (int i = 1; i <= DEFAULT_PORT_COUNT; i++) {
      specs.add(new PortSpec("Port" + i, PortType.FAST_ETHERNET, true, true));
    }
    return specs;
  }
}
