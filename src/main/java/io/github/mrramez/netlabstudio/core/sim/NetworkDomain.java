package io.github.mrramez.netlabstudio.core.sim;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One collision domain or one broadcast domain: the ports and links that belong together. The UI
 * colours the links of a domain with one colour.
 *
 * @param id {@code collision-1}, {@code broadcast-2}, ... numbered in the order the domains appear
 *     in the topology
 * @param portIds the ids ({@code deviceId/portName}) of the ports in the domain
 * @param linkIds the ids of the links in the domain
 * @param deviceIds the ids of the devices with at least one port in the domain; a switch or router
 *     belongs to several domains
 */
public record NetworkDomain(
    String id, Set<String> portIds, Set<String> linkIds, Set<String> deviceIds) {

  /** Copies the sets (keeping their order) so a domain can never change. */
  public NetworkDomain {
    Objects.requireNonNull(id, "id");
    portIds = Collections.unmodifiableSet(new LinkedHashSet<>(portIds));
    linkIds = Collections.unmodifiableSet(new LinkedHashSet<>(linkIds));
    deviceIds = Collections.unmodifiableSet(new LinkedHashSet<>(deviceIds));
  }
}
