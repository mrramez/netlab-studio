package io.github.mrramez.netlabstudio.core.net;

import java.util.Objects;

/**
 * The outcome of dividing a base network into equal subnets by borrowing host bits.
 *
 * @param base the network that was divided
 * @param subnetMask the mask every subnet gets
 * @param borrowedBits how many host bits of the base became subnet bits
 * @param subnetCount how many subnets this gives: 2<sup>borrowed bits</sup> (the all-zeros and
 *     all-ones subnets are counted, as modern IOS does)
 * @param hostsPerSubnet how many hosts fit in each subnet: 2<sup>host bits</sup> - 2
 */
public record SubnetPlan(
    Ipv4Network base,
    SubnetMask subnetMask,
    int borrowedBits,
    long subnetCount,
    long hostsPerSubnet) {

  /**
   * Checks that the pieces belong together.
   *
   * @throws IllegalArgumentException if the borrowed bits do not match the two masks
   */
  public SubnetPlan {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(subnetMask, "subnetMask");
    if (borrowedBits < 0 || borrowedBits != subnetMask.prefixLength() - base.prefixLength()) {
      throw new IllegalArgumentException(
          "borrowed bits "
              + borrowedBits
              + " do not match "
              + base.mask().toCidrString()
              + " -> "
              + subnetMask.toCidrString());
    }
  }

  /** The host bits left in each subnet. */
  public int hostBits() {
    return subnetMask.hostBits();
  }

  /** The step between consecutive subnets in the {@link #interestingOctet()}. */
  public int blockSize() {
    return firstSubnet().blockSize();
  }

  /** The octet (1 to 4) in which the subnets step by {@link #blockSize()}. */
  public int interestingOctet() {
    return firstSubnet().interestingOctet();
  }

  /** The first (lowest) subnet. */
  public Ipv4Network firstSubnet() {
    return new Ipv4Network(base.network(), subnetMask);
  }
}
