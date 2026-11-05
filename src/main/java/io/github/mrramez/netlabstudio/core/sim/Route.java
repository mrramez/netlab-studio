package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.net.Ipv4Network;
import java.util.Objects;

/**
 * One row of a routing table. In this phase every route is a connected route: it exists while the
 * interface has an address and is up/up, and traffic for it is delivered directly (Cisco shows
 * these as {@code C}).
 *
 * @param destination the network the route covers
 * @param interfaceName the interface the traffic leaves through
 */
public record Route(Ipv4Network destination, String interfaceName) {

  /** Rejects missing parts. */
  public Route {
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(interfaceName, "interfaceName");
  }
}
