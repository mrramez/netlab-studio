package io.github.mrramez.netlabstudio.core.model;

/**
 * Which wire pairs an Ethernet port transmits on (the "pin-out").
 *
 * <p>A port that transmits on pins 1 and 2 is {@link #MDI}; one that receives there is {@link
 * #MDI_X}. A cable must join a transmitter to a receiver: a straight-through cable does that
 * between an MDI and an MDI-X port, a crossover cable swaps the pairs for two ports of the same
 * kind.
 */
public enum MdiType {
  /** Transmits on pins 1 and 2: PC and server NICs, router Ethernet interfaces. */
  MDI,
  /** Receives on pins 1 and 2: hub, repeater, bridge and switch ports. */
  MDI_X
}
