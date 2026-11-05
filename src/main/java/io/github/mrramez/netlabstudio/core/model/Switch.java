package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.MacGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * A layer-2 switch: 24 Fast Ethernet ports ({@code Fa0/1} to {@code Fa0/24}), 2 Gigabit uplinks
 * ({@code G0/1}, {@code G0/2}) and a console port. All ports start enabled. Every port is its own
 * collision domain and the whole switch is one broadcast domain (VLANs come in a later phase).
 */
public final class Switch extends Device {

  /** The name of the console port. */
  public static final String CONSOLE_NAME = "Console";

  /** The number of Fast Ethernet ports on a new switch. */
  public static final int FAST_ETHERNET_COUNT = 24;

  /** The number of Gigabit Ethernet ports on a new switch. */
  public static final int GIGABIT_COUNT = 2;

  Switch(String id, String name, MacGenerator macs) {
    super(id, name, DeviceType.SWITCH, portSpecs(), macs);
  }

  private static List<PortSpec> portSpecs() {
    List<PortSpec> specs = new ArrayList<>(FAST_ETHERNET_COUNT + GIGABIT_COUNT + 1);
    for (int i = 1; i <= FAST_ETHERNET_COUNT; i++) {
      specs.add(new PortSpec("Fa0/" + i, PortType.FAST_ETHERNET, true, true));
    }
    for (int i = 1; i <= GIGABIT_COUNT; i++) {
      specs.add(new PortSpec("G0/" + i, PortType.GIGABIT_ETHERNET, true, true));
    }
    specs.add(new PortSpec(CONSOLE_NAME, PortType.CONSOLE, false, true));
    return specs;
  }
}
