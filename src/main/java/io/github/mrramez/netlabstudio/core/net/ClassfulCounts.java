package io.github.mrramez.netlabstudio.core.net;

import java.util.Objects;

/**
 * Subnet and host counts the way the course exams ask for them: measured against the address's
 * classful default mask, with 2<sup>s</sup> subnets and 2<sup>h</sup> - 2 hosts.
 *
 * <p>When the mask is shorter than the class default (for example {@code /17} on a class C address)
 * the network is a supernet: it joins several classful networks instead of splitting one, so
 * nothing is borrowed and there is one block.
 *
 * <p>The host count is the classic formula, clamped at 0: a {@code /31} gives 0 and a {@code /32}
 * gives 0 here, although RFC 3021 lets a /31 link two routers (see {@link
 * Ipv4Network#usableHostCount()}).
 *
 * @param ipClass the class of the address
 * @param defaultMask the classful default mask of that class
 * @param mask the mask being analysed
 * @param borrowedBits how many bits {@code mask} adds to the default mask (0 for a supernet)
 * @param subnetCount 2<sup>borrowed bits</sup> (1 for a supernet)
 * @param hostBits 32 minus the prefix length of {@code mask}
 * @param hostsPerSubnet 2<sup>host bits</sup> - 2, but not below 0
 * @param supernetBits how many bits {@code mask} is shorter than the default mask (0 if it is not)
 */
public record ClassfulCounts(
    IpClass ipClass,
    SubnetMask defaultMask,
    SubnetMask mask,
    int borrowedBits,
    long subnetCount,
    int hostBits,
    long hostsPerSubnet,
    int supernetBits) {

  /** Rejects missing values. */
  public ClassfulCounts {
    Objects.requireNonNull(ipClass, "ipClass");
    Objects.requireNonNull(defaultMask, "defaultMask");
    Objects.requireNonNull(mask, "mask");
  }

  /** Whether the mask is shorter than the class default, i.e. the network is a supernet. */
  public boolean isSupernet() {
    return supernetBits > 0;
  }

  /** How many classful networks a supernet joins: 2<sup>supernet bits</sup> (1 if none). */
  public long combinedNetworks() {
    return 1L << supernetBits;
  }
}
