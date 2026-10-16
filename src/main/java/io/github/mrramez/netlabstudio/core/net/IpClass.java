package io.github.mrramez.netlabstudio.core.net;

import java.util.Objects;
import java.util.Optional;

/**
 * The classful address classes A to E (RFC 791), still used by the course to find a default mask
 * and to count "borrowed" bits. Real networks have used CIDR (RFC 4632) instead of classes since
 * the 1990s.
 *
 * <p>The class is decided by the leading bits of the first octet:
 *
 * <pre>
 * A  0xxxxxxx  first octet   0-127   default mask /8
 * B  10xxxxxx  first octet 128-191   default mask /16
 * C  110xxxxx  first octet 192-223   default mask /24
 * D  1110xxxx  first octet 224-239   multicast, no mask
 * E  1111xxxx  first octet 240-255   reserved, no mask
 * </pre>
 *
 * <p>Class A's range includes {@code 0.0.0.0/8} ("this network") and {@code 127.0.0.0/8}
 * (loopback), which are never ordinary host networks; many textbooks therefore write 1-126.
 */
public enum IpClass {
  /** Large networks: 8 network bits. */
  A(0, 127, "0", 8),
  /** Medium networks: 16 network bits. */
  B(128, 191, "10", 16),
  /** Small networks: 24 network bits. */
  C(192, 223, "110", 24),
  /** Multicast groups. */
  D(224, 239, "1110", -1),
  /** Reserved for future use (experimental). */
  E(240, 255, "1111", -1);

  private final int firstOctetLow;
  private final int firstOctetHigh;
  private final String leadingBits;
  private final int defaultPrefix;

  IpClass(int firstOctetLow, int firstOctetHigh, String leadingBits, int defaultPrefix) {
    this.firstOctetLow = firstOctetLow;
    this.firstOctetHigh = firstOctetHigh;
    this.leadingBits = leadingBits;
    this.defaultPrefix = defaultPrefix;
  }

  /** The class of an address, decided by its first octet. */
  public static IpClass of(Ipv4Address address) {
    Objects.requireNonNull(address, "address");
    int firstOctet = address.octet(1);
    for (IpClass ipClass : values()) {
      if (firstOctet <= ipClass.firstOctetHigh) {
        return ipClass;
      }
    }
    // Unreachable: class E ends at 255.
    throw new IllegalStateException("no class for first octet " + firstOctet);
  }

  /** The lowest first-octet value of this class. */
  public int firstOctetLow() {
    return firstOctetLow;
  }

  /** The highest first-octet value of this class. */
  public int firstOctetHigh() {
    return firstOctetHigh;
  }

  /** The bit pattern every first octet of this class starts with, e.g. {@code 110} for class C. */
  public String leadingBits() {
    return leadingBits;
  }

  /** The classful default mask; empty for classes D and E, which have no network/host split. */
  public Optional<SubnetMask> defaultMask() {
    return defaultPrefix < 0 ? Optional.empty() : Optional.of(SubnetMask.ofPrefix(defaultPrefix));
  }

  /** Whether addresses of this class identify a single host (classes A, B and C). */
  public boolean isUnicast() {
    return defaultPrefix >= 0;
  }

  /** Whether this is the multicast class (D). */
  public boolean isMulticast() {
    return this == D;
  }

  /** Whether this class is reserved for future use (E). */
  public boolean isReserved() {
    return this == E;
  }

  /** The RFC 1918 private block that lies in this class, if any. */
  public Optional<Ipv4Network> privateRange() {
    return switch (this) {
      case A -> Optional.of(Ipv4Network.parse("10.0.0.0/8"));
      case B -> Optional.of(Ipv4Network.parse("172.16.0.0/12"));
      case C -> Optional.of(Ipv4Network.parse("192.168.0.0/16"));
      case D, E -> Optional.empty();
    };
  }

  /** The loopback block {@code 127.0.0.0/8}, which lies in class A. */
  public Optional<Ipv4Network> loopbackRange() {
    return this == A ? Optional.of(Ipv4Network.parse("127.0.0.0/8")) : Optional.empty();
  }
}
