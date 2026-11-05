package io.github.mrramez.netlabstudio.core.model;

/** The cable types the simulator offers. */
public enum CableType {
  /** UTP with the same wiring at both ends: joins an MDI port to an MDI-X port. */
  STRAIGHT,
  /** UTP with the transmit and receive pairs swapped: joins two ports of the same kind. */
  CROSSOVER,
  /** Serial cable between two router serial interfaces. */
  SERIAL,
  /** Rollover cable from a PC's serial port to a device's console port; carries no network data. */
  CONSOLE;

  /** Whether the cable is UTP, i.e. can carry Ethernet frames between two Ethernet ports. */
  public boolean isEthernet() {
    return this == STRAIGHT || this == CROSSOVER;
  }
}
