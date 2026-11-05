package io.github.mrramez.netlabstudio.core.model;

/** The kinds of device that can be placed in a topology. */
public enum DeviceType {
  /** A personal computer. */
  PC(7, MdiType.MDI),
  /** A server; behaves like a PC in the simulator. */
  SERVER(7, MdiType.MDI),
  /** A multiport repeater: one shared collision domain. */
  HUB(1, MdiType.MDI_X),
  /** A two-port signal regenerator. */
  REPEATER(1, MdiType.MDI_X),
  /** A learning bridge. */
  BRIDGE(2, MdiType.MDI_X),
  /** A layer-2 switch. */
  SWITCH(2, MdiType.MDI_X),
  /** A layer-3 router. */
  ROUTER(3, MdiType.MDI);

  private final int layer;
  private final MdiType mdi;

  DeviceType(int layer, MdiType mdi) {
    this.layer = layer;
    this.mdi = mdi;
  }

  /** The highest OSI layer the device works at (hubs 1, switches 2, routers 3, hosts 7). */
  public int osiLayer() {
    return layer;
  }

  /** The pin-out of the device's Ethernet ports. */
  public MdiType mdi() {
    return mdi;
  }

  /** Whether this is a PC or a server, i.e. a device that originates traffic. */
  public boolean isHost() {
    return this == PC || this == SERVER;
  }
}
