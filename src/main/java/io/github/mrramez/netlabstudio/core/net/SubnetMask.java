package io.github.mrramez.netlabstudio.core.net;

import java.util.Objects;

/**
 * An IPv4 subnet mask: a block of 1s followed by a block of 0s (RFC 4632).
 *
 * <p>Stored as the number of 1 bits (the prefix length), which is all a valid mask carries; a mask
 * with a 1 after a 0 cannot be built.
 *
 * @param prefixLength the number of leading 1 bits, from 0 to 32
 */
public record SubnetMask(int prefixLength) {

  /**
   * Validates the prefix length.
   *
   * @throws InvalidAddressException if it is outside 0 to 32
   */
  public SubnetMask {
    if (prefixLength < 0 || prefixLength > 32) {
      throw new InvalidAddressException(
          "prefix length " + prefixLength + " is not valid: it must be between 0 and 32");
    }
  }

  /**
   * A mask with {@code prefixLength} leading 1 bits.
   *
   * @throws InvalidAddressException if it is outside 0 to 32
   */
  public static SubnetMask ofPrefix(int prefixLength) {
    return new SubnetMask(prefixLength);
  }

  /**
   * Parses either {@code /24} (prefix length) or {@code 255.255.255.0} (dotted decimal).
   *
   * @throws InvalidAddressException if the text is neither, or the dotted form is not a valid mask
   */
  public static SubnetMask parse(String text) {
    Objects.requireNonNull(text, "text");
    if (text.startsWith("/")) {
      return parsePrefix(text);
    }
    if (isAsciiDigits(text)) {
      // "24" could be meant as either form, so ask instead of guessing.
      throw new InvalidAddressException(
          InvalidAddressException.quoted(text)
              + " is ambiguous: write /"
              + text
              + " for a prefix length, or a dotted mask such as 255.255.255.0");
    }
    return fromDotted(text);
  }

  /**
   * Parses a dotted-decimal mask.
   *
   * @throws InvalidAddressException if the text is not an address, or its 1 bits are not one
   *     contiguous block at the left
   */
  public static SubnetMask fromDotted(String text) {
    Objects.requireNonNull(text, "text");
    return fromAddress(Ipv4Address.parse(text));
  }

  /**
   * Reads an address as a mask.
   *
   * @throws InvalidAddressException if its 1 bits are not one contiguous block at the left
   */
  public static SubnetMask fromAddress(Ipv4Address address) {
    Objects.requireNonNull(address, "address");
    if (!isValidMask(address)) {
      throw new InvalidAddressException(
          InvalidAddressException.quoted(address.toString())
              + " is not a valid subnet mask: in binary it is "
              + address.toBinaryString()
              + ", and a mask must be a block of 1s followed by a block of 0s");
    }
    return new SubnetMask(Integer.bitCount(address.value()));
  }

  /** Whether the address is a valid mask, i.e. its 1 bits form one block at the left. */
  public static boolean isValidMask(Ipv4Address address) {
    int bits = address.value();
    return bits == maskBits(Integer.bitCount(bits));
  }

  /** The mask as a 32-bit pattern: {@code prefixLength} ones, then zeros. */
  public int toInt() {
    return maskBits(prefixLength);
  }

  /** The mask in dotted form, e.g. {@code 255.255.240.0}. */
  public Ipv4Address toAddress() {
    return new Ipv4Address(toInt());
  }

  /** The wildcard (inverse) mask used by access lists and OSPF, e.g. {@code 0.0.15.255}. */
  public Ipv4Address wildcard() {
    return new Ipv4Address(~toInt());
  }

  /** The number of host bits: 32 minus the prefix length. */
  public int hostBits() {
    return 32 - prefixLength;
  }

  /** The prefix length in slash form, e.g. {@code /20}. */
  public String toCidrString() {
    return "/" + prefixLength;
  }

  /** The mask in dotted form. */
  @Override
  public String toString() {
    return toAddress().toString();
  }

  // A shift by 32 would wrap to a shift by 0 in Java, so /0 needs its own case.
  private static int maskBits(int ones) {
    return ones == 0 ? 0 : -1 << (32 - ones);
  }

  private static SubnetMask parsePrefix(String text) {
    String digits = text.substring(1);
    if (digits.isEmpty()) {
      throw new InvalidAddressException(
          InvalidAddressException.quoted(text) + " has no prefix length after the slash");
    }
    // At most two digits keeps "/0033"-style input out and rules out overflow.
    if (digits.length() > 2 || !isAsciiDigits(digits) || Integer.parseInt(digits) > 32) {
      throw new InvalidAddressException(
          InvalidAddressException.quoted(text)
              + " is not a valid prefix length: write a whole number between 0 and 32 after the"
              + " slash");
    }
    return new SubnetMask(Integer.parseInt(digits));
  }

  // Not Character.isDigit, which also accepts digits of other scripts.
  private static boolean isAsciiDigits(String text) {
    if (text.isEmpty()) {
      return false;
    }
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }
}
