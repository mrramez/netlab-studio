package io.github.mrramez.netlabstudio.core.model;

import java.util.Objects;

/**
 * A cable between two ports of a {@link Topology}.
 *
 * <p>Whether the link works is not stored: it follows from the cable type, the {@link CableRules}
 * of the topology and the configuration of both ports, so {@link #state()} is always current.
 */
public final class Link {

  private final Topology owner;
  private final String id;
  private final Port portA;
  private final Port portB;
  private final CableType cable;
  private boolean attached = true;

  Link(Topology owner, String id, Port portA, Port portB, CableType cable) {
    this.owner = owner;
    this.id = id;
    this.portA = portA;
    this.portB = portB;
    this.cable = cable;
  }

  /** A unique id within the topology. */
  public String id() {
    return id;
  }

  /** One end. */
  public Port portA() {
    return portA;
  }

  /** The other end. */
  public Port portB() {
    return portB;
  }

  /** The cable type. */
  public CableType cable() {
    return cable;
  }

  /**
   * The end that is not {@code port}.
   *
   * @throws IllegalArgumentException if {@code port} is not an end of this link
   */
  public Port other(Port port) {
    Objects.requireNonNull(port, "port");
    if (port == portA) {
      return portB;
    }
    if (port == portB) {
      return portA;
    }
    throw new IllegalArgumentException(port + " is not an end of link " + id);
  }

  /** The current state; {@link LinkState#DOWN} once the link has been removed. */
  public LinkState state() {
    return attached ? owner.diagnose(portA).state() : LinkState.DOWN;
  }

  /** Whether the link is {@link LinkState#UP}. */
  public boolean isUp() {
    return state() == LinkState.UP;
  }

  /**
   * Whether this link can carry Ethernet frames in the simulation: it is up and joins two Ethernet
   * ports with a UTP cable. Serial and console links are modelled but carry no simulated traffic.
   */
  public boolean carriesFrames() {
    return cable.isEthernet() && portA.type().isEthernet() && isUp();
  }

  void detach() {
    attached = false;
  }

  @Override
  public String toString() {
    return id + " (" + portA.id() + " <-" + cable + "-> " + portB.id() + ")";
  }
}
