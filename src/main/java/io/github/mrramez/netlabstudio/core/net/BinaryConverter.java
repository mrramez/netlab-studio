package io.github.mrramez.netlabstudio.core.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Binary and decimal conversion for addresses, with worked solutions that show the place values
 * (128 64 32 16 8 4 2 1).
 *
 * <p>Explanation keys and their arguments:
 *
 * <ul>
 *   <li>{@code net.binary.start} (octet): begin converting a decimal octet
 *   <li>{@code net.binary.octet} (position, octet): same, as part of a whole address
 *   <li>{@code net.binary.placeValue} (place value, fits, remainder): does the place value fit into
 *       what is left of the number, and what remains afterwards
 *   <li>{@code net.binary.octetResult} (octet, binary): the finished octet
 *   <li>{@code net.binary.addressResult} (address, binary): the finished address
 *   <li>{@code net.binary.toDecimal} (position, group, sum, octet): one binary group as a sum of
 *       place values
 *   <li>{@code net.binary.addressFromBinary} (binary, address): the finished address
 * </ul>
 */
public final class BinaryConverter {

  private static final int OCTET_BITS = 8;
  private static final int MAX_WIDTH = 63;
  // "xxxxxxxx.xxxxxxxx.xxxxxxxx.xxxxxxxx"
  private static final int BINARY_ADDRESS_LENGTH = 4 * OCTET_BITS + 3;

  private BinaryConverter() {}

  /**
   * The value in binary, padded with leading zeros to {@code width} digits.
   *
   * @param width from 1 to 63
   * @throws IllegalArgumentException if the value is negative or does not fit in {@code width} bits
   */
  public static String toBinary(long value, int width) {
    if (width < 1 || width > MAX_WIDTH) {
      throw new IllegalArgumentException("width must be between 1 and 63, was " + width);
    }
    if (value < 0 || value >= (1L << width)) {
      throw new IllegalArgumentException(
          "value "
              + value
              + " does not fit in "
              + width
              + " bits (0 to "
              + ((1L << width) - 1)
              + ")");
    }
    String digits = Long.toBinaryString(value);
    return "0".repeat(width - digits.length()) + digits;
  }

  /**
   * Reads a string of 0s and 1s as a number.
   *
   * @throws IllegalArgumentException if the text is empty, longer than 63 digits, or has another
   *     character
   */
  public static long fromBinary(String bits) {
    Objects.requireNonNull(bits, "bits");
    if (bits.isEmpty()) {
      throw new IllegalArgumentException("a binary number needs at least one digit");
    }
    if (bits.length() > MAX_WIDTH) {
      throw new IllegalArgumentException(
          "a binary number has at most 63 digits here, got " + bits.length());
    }
    for (int i = 0; i < bits.length(); i++) {
      char c = bits.charAt(i);
      if (c != '0' && c != '1') {
        throw new IllegalArgumentException(
            "a binary number may only contain 0 and 1, but digit " + (i + 1) + " is different");
      }
    }
    return Long.parseLong(bits, 2);
  }

  /**
   * One octet as exactly eight binary digits.
   *
   * @throws IllegalArgumentException if it is outside 0 to 255
   */
  public static String octetToBinary(int octet) {
    return toBinary(octet, OCTET_BITS);
  }

  /** The address as four dotted 8-bit groups. */
  public static String ipToBinary(Ipv4Address address) {
    Objects.requireNonNull(address, "address");
    return octetToBinary(address.octet(1))
        + "."
        + octetToBinary(address.octet(2))
        + "."
        + octetToBinary(address.octet(3))
        + "."
        + octetToBinary(address.octet(4));
  }

  /**
   * Reads four dotted 8-bit groups as an address.
   *
   * @throws InvalidAddressException if there are not exactly four groups of eight 0/1 digits
   */
  public static Ipv4Address ipFromBinary(String dotted) {
    Objects.requireNonNull(dotted, "dotted");
    if (dotted.isEmpty() || dotted.length() > BINARY_ADDRESS_LENGTH) {
      throw invalidBinary(dotted, "expected 4 groups of 8 binary digits separated by dots");
    }
    String[] groups = dotted.split("\\.", -1);
    if (groups.length != 4) {
      throw invalidBinary(dotted, "expected 4 groups separated by dots but found " + groups.length);
    }
    int[] octets = new int[4];
    for (int i = 0; i < 4; i++) {
      String group = groups[i];
      if (group.length() != OCTET_BITS) {
        throw invalidBinary(
            dotted, "group " + (i + 1) + " has " + group.length() + " digits instead of 8");
      }
      for (int j = 0; j < OCTET_BITS; j++) {
        char c = group.charAt(j);
        if (c != '0' && c != '1') {
          throw invalidBinary(dotted, "group " + (i + 1) + " may only contain 0 and 1");
        }
      }
      octets[i] = Integer.parseInt(group, 2);
    }
    return Ipv4Address.of(octets[0], octets[1], octets[2], octets[3]);
  }

  /**
   * One octet in binary, explained by the place values 128 to 1: each one that fits into what is
   * left of the number gets a 1 and is subtracted.
   */
  public static Explained<String> octetToBinaryExplained(int octet) {
    return explainOctet(octet, ExplanationStep.of("net.binary.start", octet));
  }

  /** The address in binary, explained octet by octet. */
  public static Explained<String> ipToBinaryExplained(Ipv4Address address) {
    Objects.requireNonNull(address, "address");
    List<ExplanationStep> steps = new ArrayList<>();
    for (int position = 1; position <= 4; position++) {
      int octet = address.octet(position);
      steps.addAll(
          explainOctet(octet, ExplanationStep.of("net.binary.octet", position, octet)).steps());
    }
    String binary = ipToBinary(address);
    steps.add(ExplanationStep.of("net.binary.addressResult", address.toString(), binary));
    return new Explained<>(binary, steps);
  }

  /** Four binary groups as an address, explained by summing the place values of the 1 bits. */
  public static Explained<Ipv4Address> ipFromBinaryExplained(String dotted) {
    Ipv4Address address = ipFromBinary(dotted);
    List<ExplanationStep> steps = new ArrayList<>();
    String[] groups = dotted.split("\\.", -1);
    for (int i = 0; i < 4; i++) {
      steps.add(
          ExplanationStep.of(
              "net.binary.toDecimal",
              i + 1,
              groups[i],
              placeValueSum(groups[i]),
              address.octet(i + 1)));
    }
    steps.add(ExplanationStep.of("net.binary.addressFromBinary", dotted, address.toString()));
    return new Explained<>(address, steps);
  }

  private static Explained<String> explainOctet(int octet, ExplanationStep first) {
    String binary = octetToBinary(octet);
    List<ExplanationStep> steps = new ArrayList<>();
    steps.add(first);
    int remainder = octet;
    for (int place = 128; place >= 1; place /= 2) {
      boolean fits = remainder >= place;
      if (fits) {
        remainder -= place;
      }
      steps.add(ExplanationStep.of("net.binary.placeValue", place, fits, remainder));
    }
    steps.add(ExplanationStep.of("net.binary.octetResult", octet, binary));
    return new Explained<>(binary, steps);
  }

  // "128+32+16+8+1" for 10100... style groups; "0" when no bit is set.
  private static String placeValueSum(String group) {
    StringBuilder sum = new StringBuilder();
    for (int i = 0; i < OCTET_BITS; i++) {
      if (group.charAt(i) == '1') {
        if (sum.length() > 0) {
          sum.append('+');
        }
        sum.append(1 << (OCTET_BITS - 1 - i));
      }
    }
    return sum.length() == 0 ? "0" : sum.toString();
  }

  private static InvalidAddressException invalidBinary(String text, String reason) {
    return new InvalidAddressException(
        InvalidAddressException.quoted(text) + " is not a valid binary IPv4 address: " + reason);
  }
}
