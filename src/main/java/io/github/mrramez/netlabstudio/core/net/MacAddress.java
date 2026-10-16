package io.github.mrramez.netlabstudio.core.net;

import java.util.Locale;
import java.util.Objects;

/**
 * A 48-bit IEEE 802 MAC address.
 *
 * <p>The first three bytes are the OUI (the vendor that made the interface); the last three are
 * assigned by that vendor. Two bits of the first byte have a meaning of their own:
 *
 * <ul>
 *   <li>the lowest bit (I/G): 1 means a group address, i.e. multicast; broadcast is the special
 *       case where all 48 bits are 1
 *   <li>the next bit (U/L): 1 means the address was set locally instead of by the vendor
 * </ul>
 *
 * @param value the 48 address bits; the first byte on the wire is the most significant
 */
public record MacAddress(long value) {

  private static final long MAX = 0xFFFF_FFFF_FFFFL;
  private static final int MAX_TEXT_LENGTH = "00:1a:2b:3c:4d:5e".length();
  private static final String ACCEPTED_FORMS =
      "expected 00:1a:2b:3c:4d:5e, 00-1a-2b-3c-4d-5e or 001a.2b3c.4d5e";

  /** {@code ff:ff:ff:ff:ff:ff}: every station on the local network accepts a frame sent here. */
  public static final MacAddress BROADCAST = new MacAddress(MAX);

  /**
   * Validates the value.
   *
   * @throws InvalidAddressException if it does not fit in 48 bits
   */
  public MacAddress {
    if (value < 0 || value > MAX) {
      throw new InvalidAddressException("MAC address value " + value + " does not fit in 48 bits");
    }
  }

  /**
   * Parses the colon, hyphen or Cisco dotted form, in upper or lower case. The separators must not
   * be mixed.
   *
   * @throws InvalidAddressException if the text is none of these forms
   */
  public static MacAddress parse(String text) {
    Objects.requireNonNull(text, "text");
    if (text.isBlank()) {
      throw invalid(text, "it is empty");
    }
    if (text.length() > MAX_TEXT_LENGTH) {
      throw invalid(text, "it is too long");
    }
    MacStyle style = styleOf(text);
    int groupSize = style.groupSize();
    int groups = style.groupCount();
    int expectedLength = groups * groupSize + (groups - 1);
    if (text.length() != expectedLength) {
      throw invalid(text, "this form has " + expectedLength + " characters");
    }
    long result = 0;
    for (int i = 0; i < text.length(); i++) {
      boolean separatorPosition = (i + 1) % (groupSize + 1) == 0;
      char c = text.charAt(i);
      if (separatorPosition) {
        if (c != style.separator()) {
          throw invalid(text, "character " + (i + 1) + " must be '" + style.separator() + "'");
        }
      } else {
        int digit = hexValue(c);
        if (digit < 0) {
          throw invalid(text, "character " + (i + 1) + " is not a hexadecimal digit");
        }
        result = (result << 4) | digit;
      }
    }
    return new MacAddress(result);
  }

  /**
   * Builds an address from its six bytes, first byte first.
   *
   * @throws InvalidAddressException if the array does not have exactly 6 bytes
   */
  public static MacAddress fromBytes(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    if (bytes.length != 6) {
      throw new InvalidAddressException(
          "a MAC address has 6 bytes, but " + bytes.length + " were given");
    }
    long result = 0;
    for (byte b : bytes) {
      result = (result << 8) | (b & 0xFF);
    }
    return new MacAddress(result);
  }

  /** The six bytes of the address, first byte first. The array is a copy. */
  public byte[] toBytes() {
    byte[] bytes = new byte[6];
    for (int i = 0; i < 6; i++) {
      bytes[i] = (byte) (value >>> (8 * (5 - i)));
    }
    return bytes;
  }

  /** The address in the given style, in lower case. */
  public String format(MacStyle style) {
    return format(style, false);
  }

  /** The address in the given style, in upper or lower case. */
  public String format(MacStyle style, boolean upperCase) {
    Objects.requireNonNull(style, "style");
    String hex = hex(value, 12);
    StringBuilder text = new StringBuilder();
    for (int group = 0; group < style.groupCount(); group++) {
      if (group > 0) {
        text.append(style.separator());
      }
      text.append(hex, group * style.groupSize(), (group + 1) * style.groupSize());
    }
    return upperCase ? text.toString().toUpperCase(Locale.ROOT) : text.toString();
  }

  /** The vendor part (first three bytes) in colon form, e.g. {@code 00:1a:2b}. */
  public String oui() {
    return colonGroups(hex(value >>> 24, 6));
  }

  /** The part the vendor assigned (last three bytes) in colon form, e.g. {@code 3c:4d:5e}. */
  public String deviceIdentifier() {
    return colonGroups(hex(value & 0xFF_FFFFL, 6));
  }

  /** Whether this is {@code ff:ff:ff:ff:ff:ff}. */
  public boolean isBroadcast() {
    return value == MAX;
  }

  /** Whether this is a group address (I/G bit set); the broadcast address is one too. */
  public boolean isMulticast() {
    return ((value >>> 40) & 0x01) != 0;
  }

  /** Whether this addresses a single interface: the opposite of {@link #isMulticast()}. */
  public boolean isUnicast() {
    return !isMulticast();
  }

  /** Whether the address was set locally instead of burned in by the vendor (U/L bit set). */
  public boolean isLocallyAdministered() {
    return ((value >>> 40) & 0x02) != 0;
  }

  /** The address in colon form and lower case, e.g. {@code 00:1a:2b:3c:4d:5e}. */
  @Override
  public String toString() {
    return format(MacStyle.COLON);
  }

  private static MacStyle styleOf(String text) {
    MacStyle found = null;
    for (MacStyle style : MacStyle.values()) {
      if (text.indexOf(style.separator()) >= 0) {
        if (found != null) {
          throw invalid(text, "it mixes different separators");
        }
        found = style;
      }
    }
    if (found == null) {
      throw invalid(text, "it has no separators");
    }
    return found;
  }

  // Not Character.digit, which also accepts digits of other scripts and full-width letters.
  private static int hexValue(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    }
    if (c >= 'A' && c <= 'F') {
      return c - 'A' + 10;
    }
    return -1;
  }

  private static String hex(long number, int digits) {
    String hex = Long.toHexString(number);
    return "0".repeat(digits - hex.length()) + hex;
  }

  private static String colonGroups(String hex) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < hex.length(); i += 2) {
      if (i > 0) {
        text.append(':');
      }
      text.append(hex, i, i + 2);
    }
    return text.toString();
  }

  private static InvalidAddressException invalid(String text, String reason) {
    return new InvalidAddressException(
        InvalidAddressException.quoted(text)
            + " is not a valid MAC address: "
            + reason
            + " ("
            + ACCEPTED_FORMS
            + ")");
  }
}
