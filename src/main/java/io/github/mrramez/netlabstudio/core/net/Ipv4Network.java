package io.github.mrramez.netlabstudio.core.net;

import java.util.Objects;

/**
 * An IPv4 network in CIDR notation (RFC 4632): a network address and the mask that says how many
 * leading bits it fixes.
 *
 * <p>The canonical constructor requires a real network address, i.e. all host bits are 0. To find
 * the network a host belongs to, use {@link #containing} or {@link #parse}.
 *
 * <p>"Usable" addresses are the ones a host may be given. For most networks these exclude the
 * network and broadcast addresses, which is why a host count is 2<sup>h</sup> - 2. Two prefixes are
 * special: a {@code /31} has two usable addresses (RFC 3021, point-to-point links) and a {@code
 * /32} has one (a single host route).
 *
 * @param network the network address (host bits all 0)
 * @param mask the subnet mask
 */
public record Ipv4Network(Ipv4Address network, SubnetMask mask) {

  // "255.255.255.255/32"
  private static final int MAX_TEXT_LENGTH = 18;

  /**
   * Validates that {@code network} has no host bits set.
   *
   * @throws InvalidAddressException if it does
   */
  public Ipv4Network {
    Objects.requireNonNull(network, "network");
    Objects.requireNonNull(mask, "mask");
    Ipv4Address expected = network.and(mask);
    if (!expected.equals(network)) {
      throw new InvalidAddressException(
          network
              + mask.toCidrString()
              + " has host bits set; the network address would be "
              + expected
              + mask.toCidrString());
    }
  }

  /** The network that contains {@code host} under {@code mask}: the host with its host bits 0. */
  public static Ipv4Network containing(Ipv4Address host, SubnetMask mask) {
    Objects.requireNonNull(host, "host");
    Objects.requireNonNull(mask, "mask");
    return new Ipv4Network(host.and(mask), mask);
  }

  /**
   * Parses {@code address/prefix}, e.g. {@code 192.16.254.1/24}. The address may be a host address;
   * the result is the network that contains it.
   *
   * @throws InvalidAddressException if the text is not an address, a slash and a prefix length
   */
  public static Ipv4Network parse(String text) {
    Objects.requireNonNull(text, "text");
    if (text.length() > MAX_TEXT_LENGTH) {
      throw new InvalidAddressException(
          InvalidAddressException.quoted(text) + " is too long to be an address/prefix");
    }
    int slash = text.indexOf('/');
    if (slash < 0) {
      throw new InvalidAddressException(
          InvalidAddressException.quoted(text)
              + " has no prefix: write it as address/prefix, e.g. 192.168.1.0/24");
    }
    if (text.indexOf('/', slash + 1) >= 0) {
      throw new InvalidAddressException(
          InvalidAddressException.quoted(text)
              + " has more than one slash: write it as address/prefix, e.g. 192.168.1.0/24");
    }
    return containing(
        Ipv4Address.parse(text.substring(0, slash)), SubnetMask.parse(text.substring(slash)));
  }

  /** The number of leading network bits. */
  public int prefixLength() {
    return mask.prefixLength();
  }

  /** The wildcard (inverse) mask. */
  public Ipv4Address wildcard() {
    return mask.wildcard();
  }

  /** The last address of the block, which hosts use to reach every host on the network. */
  public Ipv4Address broadcast() {
    return new Ipv4Address(network.value() | ~mask.toInt());
  }

  /** The first address a host may use. */
  public Ipv4Address firstUsable() {
    return prefixLength() >= 31 ? network : network.plus(1);
  }

  /** The last address a host may use. */
  public Ipv4Address lastUsable() {
    if (prefixLength() == 32) {
      return network;
    }
    return prefixLength() == 31 ? broadcast() : broadcast().plus(-1);
  }

  /** All addresses in the block, including network and broadcast: 2<sup>host bits</sup>. */
  public long totalAddresses() {
    return 1L << mask.hostBits();
  }

  /** How many addresses hosts may use; see the class description for /31 and /32. */
  public long usableHostCount() {
    return switch (prefixLength()) {
      case 32 -> 1;
      case 31 -> 2;
      default -> totalAddresses() - 2;
    };
  }

  /** Whether {@code address} lies in this block (network and broadcast addresses included). */
  public boolean contains(Ipv4Address address) {
    Objects.requireNonNull(address, "address");
    return address.and(mask).equals(network);
  }

  /** Whether {@code other} lies entirely inside this block (or is equal to it). */
  public boolean contains(Ipv4Network other) {
    Objects.requireNonNull(other, "other");
    return other.prefixLength() >= prefixLength() && contains(other.network);
  }

  /** Whether the two blocks share any address; CIDR blocks either nest or are disjoint. */
  public boolean overlaps(Ipv4Network other) {
    Objects.requireNonNull(other, "other");
    return contains(other) || other.contains(this);
  }

  /**
   * The octet (1 to 4) in which the network part ends, where subnets step by {@link #blockSize()}:
   * {@code ceil(prefix / 8)}, at least 1. For {@code /26} it is the 4th octet; for {@code /12} the
   * 2nd; for {@code /24} the 3rd (the last full octet of the network part).
   */
  public int interestingOctet() {
    return Math.max(1, (prefixLength() + 7) / 8);
  }

  /**
   * The step between consecutive networks of this size in the {@linkplain #interestingOctet()
   * interesting octet}: 256 minus the mask's value in that octet.
   */
  public int blockSize() {
    return 256 - mask.toAddress().octet(interestingOctet());
  }

  /** The class of the network address. */
  public IpClass ipClass() {
    return IpClass.of(network);
  }

  /**
   * The adjacent block of the same size, directly after this one.
   *
   * @throws InvalidAddressException if this block is at the very end of the address space
   */
  public Ipv4Network nextNetwork() {
    try {
      return new Ipv4Network(network.plus(totalAddresses()), mask);
    } catch (InvalidAddressException e) {
      throw new InvalidAddressException(
          "there is no block of the same size after " + this + ": it ends the address space");
    }
  }

  /** The network in slash notation, e.g. {@code 10.48.0.0/12}. */
  @Override
  public String toString() {
    return network + mask.toCidrString();
  }
}
