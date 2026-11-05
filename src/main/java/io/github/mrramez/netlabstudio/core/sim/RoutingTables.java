package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.model.Port;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Builds a router's routing table from its interfaces and searches it. */
final class RoutingTables {

  private RoutingTables() {}

  /**
   * The connected routes: one per Ethernet interface that has an address and is up/up (enabled,
   * with a working cable and a working far end). Sorted by network address, then prefix length,
   * like {@code show ip route}.
   */
  static List<Route> connected(Router router) {
    List<Route> routes = new ArrayList<>();
    for (Port port : router.ports()) {
      if (port.type().isEthernet() && port.hasIpv4() && port.isLinkUp()) {
        routes.add(new Route(port.network().orElseThrow(), port.name()));
      }
    }
    routes.sort(
        Comparator.comparingLong((Route route) -> route.destination().network().toUnsignedLong())
            .thenComparingInt(route -> route.destination().prefixLength()));
    return routes;
  }

  /** The route with the longest prefix that contains {@code destination}. */
  static Optional<Route> longestMatch(List<Route> routes, Ipv4Address destination) {
    Route best = null;
    for (Route route : routes) {
      if (route.destination().contains(destination)
          && (best == null
              || route.destination().prefixLength() > best.destination().prefixLength())) {
        best = route;
      }
    }
    return Optional.ofNullable(best);
  }
}
