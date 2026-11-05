package io.github.mrramez.netlabstudio.core.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Variable Length Subnet Masking: gives each requirement a subnet just big enough for it, taken
 * from one base network.
 *
 * <p>The method is the one taught in the course: sort the requirements largest first, give each the
 * smallest block with at least {@code hosts + 2} addresses (the network and broadcast addresses are
 * not usable), and place the blocks one after another from the start of the base. Every block is a
 * power of two and the blocks never get bigger, so each starts on a boundary of its own size and
 * the plan has no gaps; it fits exactly when the blocks add up to no more than the base.
 *
 * <p>The smallest block is a {@code /30}, as in the course, even though RFC 3021 would allow a
 * {@code /31} for a point-to-point link.
 *
 * <p>Explanation keys and their arguments:
 *
 * <ul>
 *   <li>{@code net.vlsm.start} (base, number of requirements)
 *   <li>{@code net.vlsm.sort} (requirements as given, requirements largest first)
 *   <li>{@code net.vlsm.block} (position, hosts, host bits, addresses in the block, prefix, mask):
 *       the block size chosen for one requirement
 *   <li>{@code net.vlsm.allocate} (subnet, first usable, last usable, broadcast)
 *   <li>{@code net.vlsm.nextFree} (address, free addresses) or {@code net.vlsm.noFreeSpace}
 * </ul>
 */
public final class VlsmPlanner {

  /** The most requirements one plan accepts. */
  static final int MAX_REQUIREMENTS = 1024;

  private VlsmPlanner() {}

  /**
   * Plans subnets for the given host requirements.
   *
   * @param base the network to carve the subnets from
   * @param requiredHosts the number of hosts each subnet must hold, in any order
   * @throws IllegalArgumentException if there are none, more than 1024, or one is below 1
   * @throws VlsmDoesNotFitException if the base is too small
   */
  public static Explained<VlsmPlan> plan(Ipv4Network base, List<Integer> requiredHosts) {
    Objects.requireNonNull(base, "base");
    // Copying also rejects null elements and protects against the caller changing the list.
    List<Integer> hosts = List.copyOf(Objects.requireNonNull(requiredHosts, "requiredHosts"));
    if (hosts.isEmpty()) {
      throw new IllegalArgumentException("give at least one host requirement");
    }
    if (hosts.size() > MAX_REQUIREMENTS) {
      throw new IllegalArgumentException(
          "at most " + MAX_REQUIREMENTS + " requirements are allowed, got " + hosts.size());
    }
    for (int i = 0; i < hosts.size(); i++) {
      if (hosts.get(i) < 1) {
        throw new IllegalArgumentException(
            "requirement #" + (i + 1) + " must be at least 1 host, was " + hosts.get(i));
      }
    }

    // List.sort is stable, so equal requirements keep the order the student wrote them in.
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < hosts.size(); i++) {
      order.add(i);
    }
    order.sort(Comparator.comparingInt((Integer i) -> hosts.get(i)).reversed());

    List<ExplanationStep> steps = new ArrayList<>();
    steps.add(ExplanationStep.of("net.vlsm.start", base.toString(), hosts.size()));
    steps.add(
        ExplanationStep.of(
            "net.vlsm.sort",
            join(hosts),
            join(order.stream().map(hosts::get).collect(Collectors.toList()))));

    long cursor = base.network().toUnsignedLong();
    long end = base.broadcast().toUnsignedLong();
    List<VlsmAllocation> allocations = new ArrayList<>();
    for (int position = 0; position < order.size(); position++) {
      int index = order.get(position);
      int requested = hosts.get(index);
      // Smallest h with 2^h - 2 >= requested, in long arithmetic so Integer.MAX_VALUE is safe.
      int hostBits = 64 - Long.numberOfLeadingZeros(requested + 1L);
      long block = 1L << hostBits;
      long left = end - cursor + 1;
      if (block > left) {
        throw new VlsmDoesNotFitException(index, requested, block, left, base);
      }
      // Cannot fail: blocks are powers of two in descending order, so the cursor is always a
      // multiple of the next block's size.
      SubnetMask mask = SubnetMask.ofPrefix(32 - hostBits);
      Ipv4Network network = new Ipv4Network(Ipv4Address.fromUnsignedLong(cursor), mask);
      allocations.add(new VlsmAllocation(index, requested, network));
      steps.add(
          ExplanationStep.of(
              "net.vlsm.block",
              position + 1,
              requested,
              hostBits,
              block,
              mask.prefixLength(),
              mask.toString()));
      steps.add(
          ExplanationStep.of(
              "net.vlsm.allocate",
              network.toString(),
              network.firstUsable().toString(),
              network.lastUsable().toString(),
              network.broadcast().toString()));
      cursor += block;
    }

    long free = end - cursor + 1;
    Optional<Ipv4Address> nextFree =
        free > 0 ? Optional.of(Ipv4Address.fromUnsignedLong(cursor)) : Optional.empty();
    if (free > 0) {
      steps.add(ExplanationStep.of("net.vlsm.nextFree", nextFree.orElseThrow().toString(), free));
    } else {
      steps.add(ExplanationStep.of("net.vlsm.noFreeSpace"));
    }
    return new Explained<>(new VlsmPlan(base, allocations, nextFree, free), steps);
  }

  private static String join(List<Integer> numbers) {
    return numbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }
}
