package io.github.mrramez.netlabstudio.core.net;

import java.util.Objects;

/**
 * One subnet handed out by a VLSM plan.
 *
 * @param requestIndex position of the requirement in the list the caller gave (0-based), because
 *     the plan lists allocations largest first, not in input order
 * @param requestedHosts how many hosts the requirement asked for
 * @param network the subnet that was allocated for it
 */
public record VlsmAllocation(int requestIndex, int requestedHosts, Ipv4Network network) {

  /** Rejects a missing network. */
  public VlsmAllocation {
    Objects.requireNonNull(network, "network");
  }

  /** How many hosts the subnet can really hold. */
  public long usableHosts() {
    return network.usableHostCount();
  }

  /** The hosts left over after the requirement is met: the price of power-of-two block sizes. */
  public long spareHosts() {
    return usableHosts() - requestedHosts;
  }
}
