package io.github.mrramez.netlabstudio.core.net;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Route summarization: replaces several networks by the one network with the longest prefix that
 * still contains all of them.
 *
 * <p>The summary keeps the leading bits that every network address has in common, but never more
 * bits than the shortest input prefix. When the inputs do not fill the summary completely, the
 * result lists the uncovered parts, because a router that advertises the summary also attracts
 * traffic for them.
 *
 * <p>Explanation keys and their arguments:
 *
 * <ul>
 *   <li>{@code net.summary.start} (networks given, distinct networks)
 *   <li>{@code net.summary.binary} (network, address in binary)
 *   <li>{@code net.summary.commonBits} (bits all addresses share, first address in binary)
 *   <li>{@code net.summary.limitedByPrefix} (shortest input prefix): an input is shorter than the
 *       shared bits, so it sets the prefix
 *   <li>{@code net.summary.result} (summary, mask, prefix)
 *   <li>{@code net.summary.exact}, or {@code net.summary.extra} (extra addresses, extra blocks)
 *       followed by {@code net.summary.extraNetwork} (block) and, if the list was cut short, {@code
 *       net.summary.moreExtra} (blocks not shown)
 * </ul>
 */
public final class RouteSummarizer {

  /** The most networks one summary accepts. */
  static final int MAX_NETWORKS = 1024;

  private static final int MAX_EXTRA_STEPS = 32;

  private RouteSummarizer() {}

  /**
   * Summarises {@code networks} into one route.
   *
   * @throws IllegalArgumentException if the list is empty or has more than 1024 entries
   */
  public static Explained<RouteSummary> summarize(List<Ipv4Network> networks) {
    List<Ipv4Network> given = List.copyOf(Objects.requireNonNull(networks, "networks"));
    if (given.isEmpty()) {
      throw new IllegalArgumentException("give at least one network to summarise");
    }
    if (given.size() > MAX_NETWORKS) {
      throw new IllegalArgumentException(
          "at most " + MAX_NETWORKS + " networks are allowed, got " + given.size());
    }
    Set<Ipv4Network> distinctSet = new LinkedHashSet<>(given);
    List<Ipv4Network> distinct = List.copyOf(distinctSet);

    Ipv4Address first = distinct.get(0).network();
    int differing = 0;
    int shortest = 32;
    for (Ipv4Network network : distinct) {
      differing |= network.network().value() ^ first.value();
      shortest = Math.min(shortest, network.prefixLength());
    }
    int commonBits = differing == 0 ? 32 : Integer.numberOfLeadingZeros(differing);
    int prefix = Math.min(commonBits, shortest);
    Ipv4Network summary = Ipv4Network.containing(first, SubnetMask.ofPrefix(prefix));

    List<ExplanationStep> steps = new ArrayList<>();
    steps.add(ExplanationStep.of("net.summary.start", given.size(), distinct.size()));
    for (Ipv4Network network : distinct) {
      steps.add(
          ExplanationStep.of(
              "net.summary.binary", network.toString(), network.network().toBinaryString()));
    }
    steps.add(ExplanationStep.of("net.summary.commonBits", commonBits, first.toBinaryString()));
    if (commonBits > shortest) {
      steps.add(ExplanationStep.of("net.summary.limitedByPrefix", shortest));
    }
    steps.add(
        ExplanationStep.of(
            "net.summary.result", summary.toString(), summary.mask().toString(), prefix));

    List<Ipv4Network> extras = new ArrayList<>();
    collectUncovered(summary, maximal(distinct), extras);
    long extraAddresses = 0;
    for (Ipv4Network extra : extras) {
      extraAddresses += extra.totalAddresses();
    }
    if (extras.isEmpty()) {
      steps.add(ExplanationStep.of("net.summary.exact"));
    } else {
      steps.add(ExplanationStep.of("net.summary.extra", extraAddresses, extras.size()));
      for (int i = 0; i < Math.min(extras.size(), MAX_EXTRA_STEPS); i++) {
        steps.add(ExplanationStep.of("net.summary.extraNetwork", extras.get(i).toString()));
      }
      if (extras.size() > MAX_EXTRA_STEPS) {
        steps.add(ExplanationStep.of("net.summary.moreExtra", extras.size() - MAX_EXTRA_STEPS));
      }
    }
    return new Explained<>(new RouteSummary(summary, extras, extraAddresses), steps);
  }

  // CIDR blocks either nest or are disjoint, so dropping the ones inside another leaves a
  // disjoint set with the same coverage.
  private static List<Ipv4Network> maximal(List<Ipv4Network> networks) {
    List<Ipv4Network> result = new ArrayList<>();
    for (Ipv4Network candidate : networks) {
      boolean inside = false;
      for (Ipv4Network other : networks) {
        if (!other.equals(candidate) && other.contains(candidate)) {
          inside = true;
          break;
        }
      }
      if (!inside) {
        result.add(candidate);
      }
    }
    return result;
  }

  // Splits the block in halves until each piece is fully covered or fully uncovered; the
  // uncovered pieces are the fewest aligned blocks, lowest address first.
  private static void collectUncovered(
      Ipv4Network block, List<Ipv4Network> covering, List<Ipv4Network> out) {
    boolean overlaps = false;
    for (Ipv4Network input : covering) {
      if (input.contains(block)) {
        return;
      }
      overlaps |= block.contains(input);
    }
    if (!overlaps) {
      out.add(block);
      return;
    }
    // Overlapping without being covered means some input is smaller, so the block can be split.
    SubnetMask half = SubnetMask.ofPrefix(block.prefixLength() + 1);
    collectUncovered(new Ipv4Network(block.network(), half), covering, out);
    collectUncovered(
        new Ipv4Network(block.network().plus(block.totalAddresses() / 2), half), covering, out);
  }
}
