package io.github.mrramez.netlabstudio.core.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Which cable works between which ports.
 *
 * <p>For Ethernet ports the rule is about the pin-out ({@link MdiType}): an MDI port (PC, router)
 * needs a straight-through cable to an MDI-X port (hub, switch, bridge) and a crossover cable to
 * another MDI port; two MDI-X ports need a crossover cable too. With {@link #strictCabling()} the
 * wrong cable leaves the link down, as in the course labs; with {@link #autoMdix()} the ports
 * detect and swap the pairs themselves, so either UTP cable works. Serial cables join two serial
 * ports and console cables join a PC's serial port to a console port, in both modes.
 *
 * @param strict whether the cable type must match the pin-outs exactly
 */
public record CableRules(boolean strict) {

  /** The default for the labs: the wrong cable type leaves the link down. */
  public static CableRules strictCabling() {
    return new CableRules(true);
  }

  /** Auto-MDIX: either UTP cable works between two Ethernet ports. */
  public static CableRules autoMdix() {
    return new CableRules(false);
  }

  /** The cable types that work between the two ports; empty if no cable can join them. */
  public Set<CableType> workingCables(Port a, Port b) {
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(b, "b");
    if (a.type().isEthernet() && b.type().isEthernet()) {
      if (!strict) {
        return EnumSet.of(CableType.STRAIGHT, CableType.CROSSOVER);
      }
      return EnumSet.of(a.mdi() == b.mdi() ? CableType.CROSSOVER : CableType.STRAIGHT);
    }
    if (a.type() == PortType.SERIAL && b.type() == PortType.SERIAL) {
      return EnumSet.of(CableType.SERIAL);
    }
    boolean consolePair =
        (a.type() == PortType.RS232 && b.type() == PortType.CONSOLE)
            || (a.type() == PortType.CONSOLE && b.type() == PortType.RS232);
    if (consolePair) {
      return EnumSet.of(CableType.CONSOLE);
    }
    return EnumSet.noneOf(CableType.class);
  }

  /** Whether {@code cable} works between the two ports under these rules. */
  public boolean works(CableType cable, Port a, Port b) {
    Objects.requireNonNull(cable, "cable");
    return workingCables(a, b).contains(cable);
  }

  /**
   * The cable to recommend between two ports: what the pin-outs call for, independent of the strict
   * setting. Empty if the ports cannot be joined at all (e.g. Ethernet to serial).
   */
  public Optional<CableType> recommendedCable(Port a, Port b) {
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(b, "b");
    if (a.type().isEthernet() && b.type().isEthernet()) {
      return Optional.of(a.mdi() == b.mdi() ? CableType.CROSSOVER : CableType.STRAIGHT);
    }
    return strictCabling().workingCables(a, b).stream().findFirst();
  }
}
