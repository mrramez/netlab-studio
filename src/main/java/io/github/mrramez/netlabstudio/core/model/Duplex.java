package io.github.mrramez.netlabstudio.core.model;

/** The duplex setting of an Ethernet port. */
public enum Duplex {
  /** Negotiate with the other end (the default). */
  AUTO,
  /** One direction at a time. */
  HALF,
  /** Both directions at once; needs a dedicated point-to-point link. */
  FULL
}
