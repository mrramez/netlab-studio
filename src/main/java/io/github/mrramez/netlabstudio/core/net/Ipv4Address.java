package io.github.mrramez.netlabstudio.core.net;

import java.util.Objects;

/**
 * An IPv4 address (RFC 791): 32 bits, written as four decimal octets.
 *
 * <p>The record holds the raw bit pattern in an {@code int}. Java's {@code int} is signed, so use
 * {@link #toUnsignedLong()} for arithmetic and {@link #compareTo} (which compares as unsigned
 * numbers) for ordering.
 *
 * @param value the 32 bits of the address; the first octet is the most significant byte
 */
public record Ipv4Address(int value) implements Comparable<Ipv4Address> {

  private static final int MAX_TEXT_LENGTH = "255.255.255.255".length();
  private static final long MAX_UNSIGNED = 0xFFFF_FFFFL;

  /**
   * Parses dotted-decimal text such as {@code 192.168.1.45}.
   *
   * <p>Parsing is strict: exactly four octets of ASCII digits, each 0 to 255, with no spaces and no
   * leading zeros. Leading zeros are rejected because some systems read {@code 010} as octal, so
   * the same text would name different hosts.
   *
   * @throws InvalidAddressException if the text is not a valid address
   */
  public static Ipv4Address parse(String text) {
    Objects.requireNonNull(text, "text");
    if (text.isBlank()) {
      throw invalid(text, "it is empty");
    }
    if (text.length() > MAX_TEXT_LENGTH) {
      throw invalid(text, "it is too long (an address has at most 15 characters)");
    }
    String[] parts = text.split("\\.", -1);
    if (parts.length != 4) {
      throw invalid(
          text, "expected 4 octets separated by dots but found " + parts.length + " part(s)");
    }
    int[] octets = new int[4];
    for (int i = 0; i < 4; i++) {
      octets[i] = parseOctet(text, parts[i], i + 1);
    }
    return of(octets[0], octets[1], octets[2], octets[3]);
  }

  /**
   * Builds an address from its four octets.
   *
   * @throws InvalidAddressException if an octet is outside 0 to 255
   */
  public static Ipv4Address of(int first, int second, int third, int fourth) {
    int[] octets = {first, second, third, fourth};
    int bits = 0;
    for (int i = 0; i < 4; i++) {
      if (octets[i] < 0 || octets[i] > 255) {
        throw new InvalidAddressException(
            "octet " + (i + 1) + " is " + octets[i] + ", but an octet must be between 0 and 255");
      }
      bits = (bits << 8) | octets[i];
    }
    return new Ipv4Address(bits);
  }

  /**
   * Builds an address from its unsigned numeric value.
   *
   * @throws InvalidAddressException if the number is outside 0 to 2^32 - 1
   */
  public static Ipv4Address fromUnsignedLong(long unsigned) {
    if (unsigned < 0 || unsigned > MAX_UNSIGNED) {
      throw new InvalidAddressException(
          unsigned + " is outside the IPv4 address range 0 to " + MAX_UNSIGNED);
    }
    return new Ipv4Address((int) unsigned);
  }

  /**
   * One octet of the address.
   *
   * @param position 1 for the first (leftmost) octet up to 4 for the last
   */
  public int octet(int position) {
    if (position < 1 || position > 4) {
      throw new IllegalArgumentException("octet position must be 1 to 4, was " + position);
    }
    return (value >>> (8 * (4 - position))) & 0xFF;
  }

  /** The address as an unsigned number from 0 to 2^32 - 1. */
  public long toUnsignedLong() {
    return value & MAX_UNSIGNED;
  }

  /** The address as four dotted 8-bit groups, e.g. {@code 11000000.10101000.00000001.00101101}. */
  public String toBinaryString() {
    return BinaryConverter.ipToBinary(this);
  }

  /**
   * The address {@code delta} places further on (or back, when negative).
   *
   * @throws InvalidAddressException if the result would leave the IPv4 address space
   */
  public Ipv4Address plus(long delta) {
    if (delta < -MAX_UNSIGNED || delta > MAX_UNSIGNED) {
      throw new InvalidAddressException("offset " + delta + " is outside the IPv4 address space");
    }
    long moved = toUnsignedLong() + delta;
    if (moved < 0 || moved > MAX_UNSIGNED) {
      throw new InvalidAddressException(
          this + " + " + delta + " is outside the IPv4 address space");
    }
    return new Ipv4Address((int) moved);
  }

  /** The bitwise AND of this address and a mask: the network part when applied to a host. */
  public Ipv4Address and(SubnetMask mask) {
    return new Ipv4Address(value & mask.toInt());
  }

  /** {@code 0.0.0.0/8}: "this network", only valid as a source address (RFC 1122). */
  public boolean isThisNetwork() {
    return (value >>> 24) == 0;
  }

  /** {@code 10.0.0.0/8}, {@code 172.16.0.0/12} or {@code 192.168.0.0/16} (RFC 1918). */
  public boolean isPrivate() {
    return (value >>> 24) == 10 || (value >>> 20) == 0xAC1 || (value >>> 16) == 0xC0A8;
  }

  /** {@code 127.0.0.0/8}: addresses that stay on the host itself (RFC 1122). */
  public boolean isLoopback() {
    return (value >>> 24) == 127;
  }

  /** {@code 169.254.0.0/16}: self-assigned addresses when no DHCP server answers (RFC 3927). */
  public boolean isLinkLocal() {
    return (value >>> 16) == 0xA9FE;
  }

  /** {@code 224.0.0.0/4}: multicast, the old class D range (RFC 5771). */
  public boolean isMulticast() {
    return (value >>> 28) == 0xE;
  }

  /** {@code 255.255.255.255}: the limited broadcast address (RFC 919). */
  public boolean isLimitedBroadcast() {
    return value == -1;
  }

  /** {@code 240.0.0.0/4} except the limited broadcast: the old class E range (RFC 1112). */
  public boolean isReserved() {
    return (value >>> 28) == 0xF && value != -1;
  }

  @Override
  public int compareTo(Ipv4Address other) {
    return Integer.compareUnsigned(value, other.value);
  }

  @Override
  public String toString() {
    return octet(1) + "." + octet(2) + "." + octet(3) + "." + octet(4);
  }

  private static int parseOctet(String whole, String part, int position) {
    String label = "octet " + position;
    if (part.isEmpty()) {
      throw invalid(whole, label + " is empty");
    }
    for (int i = 0; i < part.length(); i++) {
      char c = part.charAt(i);
      // Not Character.isDigit: that also accepts digits of other scripts, e.g. Arabic-Indic.
      if (c < '0' || c > '9') {
        throw invalid(
            whole,
            label
                + " ("
                + InvalidAddressException.quoted(part)
                + ") contains a character that is not a digit (0-9)");
      }
    }
    if (part.length() > 1 && part.charAt(0) == '0') {
      throw invalid(
          whole,
          label
              + " ("
              + InvalidAddressException.quoted(part)
              + ") has a leading zero; write "
              + Long.parseLong(part)
              + " instead");
    }
    // At most 15 characters in total, so the digits always fit in a long.
    long number = Long.parseLong(part);
    if (number > 255) {
      throw invalid(whole, label + " is " + part + ", but an octet must be between 0 and 255");
    }
    return (int) number;
  }

  private static InvalidAddressException invalid(String text, String reason) {
    return new InvalidAddressException(
        InvalidAddressException.quoted(text) + " is not a valid IPv4 address: " + reason);
  }
}
