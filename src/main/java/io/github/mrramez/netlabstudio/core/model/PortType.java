package io.github.mrramez.netlabstudio.core.model;

/** The kind of connector a port has, which decides what cable can join it to another port. */
public enum PortType {
  /** 100 Mbit/s Ethernet over twisted pair (Cisco {@code Fa}). */
  FAST_ETHERNET,
  /** 1 Gbit/s Ethernet over twisted pair (Cisco {@code G}). */
  GIGABIT_ETHERNET,
  /** Point-to-point serial interface of a router. */
  SERIAL,
  /** Management console port of a router or switch. */
  CONSOLE,
  /** The serial (COM) port of a PC or server that a console cable plugs into. */
  RS232;

  /** Whether this is an Ethernet port, i.e. one that carries frames over a UTP cable. */
  public boolean isEthernet() {
    return this == FAST_ETHERNET || this == GIGABIT_ETHERNET;
  }
}
