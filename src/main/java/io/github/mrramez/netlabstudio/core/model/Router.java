package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.MacGenerator;
import java.util.ArrayList;
import java.util.List;

/**
 * A router. Its interfaces ({@code G0/0}, {@code G0/1}, ... and optionally {@code S0/0/0}, ...)
 * start administratively down, as on a real Cisco router, until {@code no shutdown} is applied
 * ({@link Port#setAdminUp}). Each Ethernet interface has its own MAC address. A console port is
 * always present.
 */
public final class Router extends Device {

  /** The name of the console port. */
  public static final String CONSOLE_NAME = "Console";

  /** The number of Gigabit Ethernet interfaces a router created without a count has. */
  public static final int DEFAULT_GIGABIT_COUNT = 2;

  Router(String id, String name, MacGenerator macs, int gigabit, int serial) {
    super(id, name, DeviceType.ROUTER, portSpecs(gigabit, serial), macs);
  }

  private static List<PortSpec> portSpecs(int gigabit, int serial) {
    if (gigabit < 1 || gigabit > 8 || serial < 0 || serial > 4) {
      throw new IllegalArgumentException(
          "a router has 1 to 8 Gigabit and 0 to 4 serial interfaces, but was asked for "
              + gigabit
              + " and "
              + serial);
    }
    List<PortSpec> specs = new ArrayList<>();
    for (int i = 0; i < gigabit; i++) {
      specs.add(new PortSpec("G0/" + i, PortType.GIGABIT_ETHERNET, true, false));
    }
    for (int i = 0; i < serial; i++) {
      specs.add(new PortSpec("S0/0/" + i, PortType.SERIAL, false, false));
    }
    specs.add(new PortSpec(CONSOLE_NAME, PortType.CONSOLE, false, true));
    return specs;
  }
}
