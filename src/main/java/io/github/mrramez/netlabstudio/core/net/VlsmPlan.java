package io.github.mrramez.netlabstudio.core.net;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A finished VLSM plan.
 *
 * @param base the network the subnets were carved from
 * @param allocations the subnets, largest first, which is also lowest address first
 * @param nextFree the first address after the last allocation; empty if the base is used up
 * @param freeAddresses how many addresses of the base are still unallocated
 */
public record VlsmPlan(
    Ipv4Network base,
    List<VlsmAllocation> allocations,
    Optional<Ipv4Address> nextFree,
    long freeAddresses) {

  /** Rejects missing values and copies the allocations. */
  public VlsmPlan {
    Objects.requireNonNull(base, "base");
    allocations = List.copyOf(Objects.requireNonNull(allocations, "allocations"));
    Objects.requireNonNull(nextFree, "nextFree");
  }
}
