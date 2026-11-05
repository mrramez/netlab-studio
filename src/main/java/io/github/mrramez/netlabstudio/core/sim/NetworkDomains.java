package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.model.CableType;
import io.github.mrramez.netlabstudio.core.model.Device;
import io.github.mrramez.netlabstudio.core.model.DeviceType;
import io.github.mrramez.netlabstudio.core.model.Link;
import io.github.mrramez.netlabstudio.core.model.Port;
import io.github.mrramez.netlabstudio.core.model.Topology;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The collision domains and broadcast domains of a topology, computed from the cables that are up.
 *
 * <p>Counting follows the textbook rules:
 *
 * <ul>
 *   <li>A <b>collision domain</b> is a set of ports that share one medium. Hubs and repeaters
 *       repeat everything to all their ports, so they join the links around them into one domain;
 *       every link on a bridge, switch, router or host port is a domain of its own. Four PCs on a
 *       hub are one collision domain, on a switch four. (On a full-duplex link there is in fact no
 *       collision at all; the count follows the usual exam convention.)
 *   <li>A <b>broadcast domain</b> is everything a broadcast frame reaches. Switches and bridges
 *       forward broadcasts, so they join the links around them as well; routers do not, so a router
 *       with two LANs separates two broadcast domains. (VLANs would split a switch later.)
 * </ul>
 *
 * <p>Only links that are up and carry data are counted; console cables and links that are down or
 * shut down are ignored. Domains are numbered in the order they appear in the topology.
 *
 * @param collisionDomains the collision domains
 * @param broadcastDomains the broadcast domains
 */
public record NetworkDomains(
    List<NetworkDomain> collisionDomains, List<NetworkDomain> broadcastDomains) {

  /** Copies the lists. */
  public NetworkDomains {
    collisionDomains = List.copyOf(collisionDomains);
    broadcastDomains = List.copyOf(broadcastDomains);
  }

  /** Works out both kinds of domain for the topology as it is now. */
  public static NetworkDomains analyze(Topology topology) {
    List<Link> dataLinks = new ArrayList<>();
    for (Link link : topology.links()) {
      if (link.cable() != CableType.CONSOLE && link.isUp()) {
        dataLinks.add(link);
      }
    }
    return new NetworkDomains(
        partition(topology, dataLinks, false), partition(topology, dataLinks, true));
  }

  /** The collision domain a port belongs to, if its link is counted. */
  public Optional<NetworkDomain> collisionDomainOf(Port port) {
    return find(collisionDomains, port);
  }

  /** The broadcast domain a port belongs to, if its link is counted. */
  public Optional<NetworkDomain> broadcastDomainOf(Port port) {
    return find(broadcastDomains, port);
  }

  private static Optional<NetworkDomain> find(List<NetworkDomain> domains, Port port) {
    return domains.stream().filter(domain -> domain.portIds().contains(port.id())).findFirst();
  }

  private static List<NetworkDomain> partition(
      Topology topology, List<Link> dataLinks, boolean broadcast) {
    Map<String, String> parent = new HashMap<>();
    for (Link link : dataLinks) {
      parent.put(link.portA().id(), link.portA().id());
      parent.put(link.portB().id(), link.portB().id());
    }
    for (Link link : dataLinks) {
      union(parent, link.portA().id(), link.portB().id());
    }
    for (Device device : topology.devices()) {
      if (sharesMedium(device.type(), broadcast)) {
        String first = null;
        for (Port port : device.ports()) {
          if (!parent.containsKey(port.id())) {
            continue;
          }
          if (first == null) {
            first = port.id();
          } else {
            union(parent, first, port.id());
          }
        }
      }
    }

    Map<String, List<Link>> linksByRoot = new LinkedHashMap<>();
    for (Link link : dataLinks) {
      linksByRoot
          .computeIfAbsent(find(parent, link.portA().id()), root -> new ArrayList<>())
          .add(link);
    }
    String prefix = broadcast ? "broadcast-" : "collision-";
    List<NetworkDomain> domains = new ArrayList<>();
    for (List<Link> links : linksByRoot.values()) {
      Set<String> portIds = new LinkedHashSet<>();
      Set<String> linkIds = new LinkedHashSet<>();
      Set<String> deviceIds = new LinkedHashSet<>();
      for (Link link : links) {
        linkIds.add(link.id());
        for (Port port : List.of(link.portA(), link.portB())) {
          portIds.add(port.id());
          deviceIds.add(port.device().id());
        }
      }
      domains.add(new NetworkDomain(prefix + (domains.size() + 1), portIds, linkIds, deviceIds));
    }
    return domains;
  }

  // Ports of a hub or repeater always share a medium; switches and bridges do so only for
  // broadcasts. Hosts and routers never connect their ports to each other.
  private static boolean sharesMedium(DeviceType type, boolean broadcast) {
    return switch (type) {
      case HUB, REPEATER -> true;
      case BRIDGE, SWITCH -> broadcast;
      case PC, SERVER, ROUTER -> false;
    };
  }

  private static String find(Map<String, String> parent, String id) {
    String root = id;
    while (!parent.get(root).equals(root)) {
      root = parent.get(root);
    }
    // Path compression keeps later lookups short.
    String current = id;
    while (!parent.get(current).equals(root)) {
      String next = parent.get(current);
      parent.put(current, root);
      current = next;
    }
    return root;
  }

  private static void union(Map<String, String> parent, String a, String b) {
    String rootA = find(parent, a);
    String rootB = find(parent, b);
    if (!rootA.equals(rootB)) {
      parent.put(rootB, rootA);
    }
  }
}
