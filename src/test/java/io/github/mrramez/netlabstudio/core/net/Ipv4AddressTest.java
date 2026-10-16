package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class Ipv4AddressTest {

  private static final long SEED = 20260924L;

  @ParameterizedTest
  @CsvSource({
    "0.0.0.0, 0",
    "0.0.0.1, 1",
    "10.0.0.0, 167772160",
    "127.0.0.1, 2130706433",
    "192.168.1.1, 3232235777",
    "255.255.255.255, 4294967295"
  })
  void parsesDottedDecimal(String text, long unsigned) {
    Ipv4Address address = Ipv4Address.parse(text);

    assertThat(address.toUnsignedLong()).isEqualTo(unsigned);
    assertThat(address).hasToString(text);
  }

  @Test
  void valueIsTheThirtyTwoBitPattern() {
    assertThat(Ipv4Address.of(192, 168, 1, 1).value()).isEqualTo(0xC0A80101);
    assertThat(Ipv4Address.parse("255.255.255.255").value()).isEqualTo(-1);
  }

  static Stream<Arguments> invalidAddresses() {
    return Stream.of(
        Arguments.of("", "empty"),
        Arguments.of("   ", "empty"),
        Arguments.of("1.2.3", "4 octets"),
        Arguments.of("1.2.3.4.5", "4 octets"),
        Arguments.of("1", "4 octets"),
        Arguments.of("256.1.1.1", "octet 1 is 256"),
        Arguments.of("1.2.3.999", "octet 4 is 999"),
        Arguments.of("01.2.3.4", "leading zero"),
        Arguments.of("1.2.3.04", "leading zero"),
        Arguments.of("00.0.0.0", "leading zero"),
        Arguments.of("1..2.3", "octet 2 is empty"),
        Arguments.of(".1.2.3", "octet 1 is empty"),
        Arguments.of("1.2.3.", "octet 4 is empty"),
        Arguments.of("1.2.3.a", "not a digit"),
        Arguments.of("1.2.3.-4", "not a digit"),
        Arguments.of(" 1.2.3.4", "not a digit"),
        Arguments.of("1.2.3.4 ", "not a digit"),
        Arguments.of("1.2.3.4/24", "not a digit"),
        // Arabic-Indic digits satisfy Character.isDigit but are not valid in an address.
        Arguments.of("١.٢.٣.٤", "not a digit"),
        Arguments.of("1111111111111111", "too long"),
        Arguments.of("1111.2.3.4", "octet 1 is 1111"));
  }

  @ParameterizedTest
  @MethodSource("invalidAddresses")
  void rejectsInvalidText(String text, String reason) {
    assertThatThrownBy(() -> Ipv4Address.parse(text))
        .isInstanceOf(InvalidAddressException.class)
        .hasMessageContaining(reason);
  }

  @Test
  void theMessageQuotesTheInput() {
    assertThatThrownBy(() -> Ipv4Address.parse("256.1.1.1"))
        .hasMessageContaining("'256.1.1.1'")
        .hasMessageContaining("between 0 and 255");
  }

  @Test
  void parseRejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> Ipv4Address.parse(null));
  }

  @Test
  void buildsFromFourOctets() {
    assertThat(Ipv4Address.of(10, 20, 30, 40)).hasToString("10.20.30.40");
    assertThatThrownBy(() -> Ipv4Address.of(256, 0, 0, 0))
        .isInstanceOf(InvalidAddressException.class)
        .hasMessageContaining("octet 1");
    assertThatThrownBy(() -> Ipv4Address.of(0, 0, 0, -1))
        .isInstanceOf(InvalidAddressException.class)
        .hasMessageContaining("octet 4");
  }

  @Test
  void octetsArePositionedFromOneToFour() {
    Ipv4Address address = Ipv4Address.parse("185.80.72.105");

    assertThat(address.octet(1)).isEqualTo(185);
    assertThat(address.octet(2)).isEqualTo(80);
    assertThat(address.octet(3)).isEqualTo(72);
    assertThat(address.octet(4)).isEqualTo(105);
    assertThatThrownBy(() -> address.octet(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> address.octet(5)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void buildsFromAnUnsignedLong() {
    assertThat(Ipv4Address.fromUnsignedLong(0)).hasToString("0.0.0.0");
    assertThat(Ipv4Address.fromUnsignedLong(4294967295L)).hasToString("255.255.255.255");
    assertThatThrownBy(() -> Ipv4Address.fromUnsignedLong(-1))
        .isInstanceOf(InvalidAddressException.class);
    assertThatThrownBy(() -> Ipv4Address.fromUnsignedLong(4294967296L))
        .isInstanceOf(InvalidAddressException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "185.80.72.105, 10111001.01010000.01001000.01101001",
    "39.57.109.234, 00100111.00111001.01101101.11101010",
    "192.168.1.45, 11000000.10101000.00000001.00101101",
    "158.80.164.3, 10011110.01010000.10100100.00000011",
    "0.0.0.0, 00000000.00000000.00000000.00000000",
    "255.255.255.255, 11111111.11111111.11111111.11111111"
  })
  void formatsAsDottedBinary(String text, String binary) {
    assertThat(Ipv4Address.parse(text).toBinaryString()).isEqualTo(binary);
  }

  @Test
  void ordersAddressesAsUnsignedNumbers() {
    List<Ipv4Address> sorted = new ArrayList<>();
    for (String text : List.of("200.0.0.1", "10.0.0.1", "127.255.255.255", "128.0.0.0")) {
      sorted.add(Ipv4Address.parse(text));
    }

    Collections.sort(sorted);

    assertThat(sorted.stream().map(Ipv4Address::toString))
        .containsExactly("10.0.0.1", "127.255.255.255", "128.0.0.0", "200.0.0.1");
  }

  @Test
  void movesByAnOffsetAndCarriesAcrossOctets() {
    assertThat(Ipv4Address.parse("10.0.0.255").plus(1)).hasToString("10.0.1.0");
    assertThat(Ipv4Address.parse("10.0.1.0").plus(-1)).hasToString("10.0.0.255");
    assertThat(Ipv4Address.parse("0.0.0.0").plus(4294967295L)).hasToString("255.255.255.255");
    assertThatThrownBy(() -> Ipv4Address.parse("255.255.255.255").plus(1))
        .isInstanceOf(InvalidAddressException.class)
        .hasMessageContaining("outside");
    assertThatThrownBy(() -> Ipv4Address.parse("0.0.0.0").plus(-1))
        .isInstanceOf(InvalidAddressException.class);
  }

  @Test
  void andWithAMaskGivesTheNetworkPart() {
    assertThat(Ipv4Address.parse("192.168.1.77").and(SubnetMask.ofPrefix(26)))
        .hasToString("192.168.1.64");
    assertThat(Ipv4Address.parse("10.61.219.43").and(SubnetMask.ofPrefix(12)))
        .hasToString("10.48.0.0");
    assertThat(Ipv4Address.parse("1.2.3.4").and(SubnetMask.ofPrefix(0))).hasToString("0.0.0.0");
    assertThat(Ipv4Address.parse("1.2.3.4").and(SubnetMask.ofPrefix(32))).hasToString("1.2.3.4");
  }

  @ParameterizedTest
  @CsvSource({
    "10.0.0.0, true",
    "10.255.255.255, true",
    "172.16.0.0, true",
    "172.31.255.255, true",
    "192.168.0.0, true",
    "192.168.255.255, true",
    "9.255.255.255, false",
    "11.0.0.0, false",
    "172.15.255.255, false",
    "172.32.0.0, false",
    "192.167.255.255, false",
    "192.169.0.0, false",
    "8.8.8.8, false"
  })
  void recognisesRfc1918PrivateRanges(String text, boolean expected) {
    assertThat(Ipv4Address.parse(text).isPrivate()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "127.0.0.1, true",
    "127.0.0.0, true",
    "127.255.255.255, true",
    "126.255.255.255, false",
    "128.0.0.0, false"
  })
  void recognisesLoopback(String text, boolean expected) {
    assertThat(Ipv4Address.parse(text).isLoopback()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "169.254.0.0, true",
    "169.254.255.255, true",
    "169.253.255.255, false",
    "169.255.0.0, false"
  })
  void recognisesLinkLocal(String text, boolean expected) {
    assertThat(Ipv4Address.parse(text).isLinkLocal()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "224.0.0.0, true",
    "224.0.0.5, true",
    "239.255.255.255, true",
    "223.255.255.255, false",
    "240.0.0.0, false"
  })
  void recognisesMulticast(String text, boolean expected) {
    assertThat(Ipv4Address.parse(text).isMulticast()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"255.255.255.255, true", "255.255.255.254, false", "192.168.1.255, false"})
  void recognisesTheLimitedBroadcastAddress(String text, boolean expected) {
    assertThat(Ipv4Address.parse(text).isLimitedBroadcast()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"0.0.0.0, true", "0.255.255.255, true", "1.0.0.0, false"})
  void recognisesThisNetwork(String text, boolean expected) {
    assertThat(Ipv4Address.parse(text).isThisNetwork()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "240.0.0.0, true",
    "255.255.255.254, true",
    "255.255.255.255, false",
    "239.255.255.255, false",
    "10.0.0.1, false"
  })
  void recognisesReservedClassEExceptTheBroadcast(String text, boolean expected) {
    assertThat(Ipv4Address.parse(text).isReserved()).isEqualTo(expected);
  }

  @Test
  void textAndNumberRoundTripForRandomAddresses() {
    Random random = new Random(SEED);
    for (int i = 0; i < 10_000; i++) {
      Ipv4Address address = new Ipv4Address(random.nextInt());
      String context = "seed " + SEED + ", iteration " + i + ", address " + address;

      assertThat(Ipv4Address.parse(address.toString())).as(context).isEqualTo(address);
      assertThat(Ipv4Address.fromUnsignedLong(address.toUnsignedLong()))
          .as(context)
          .isEqualTo(address);
      assertThat(BinaryConverter.ipFromBinary(address.toBinaryString()))
          .as(context)
          .isEqualTo(address);
      assertThat(address.toBinaryString()).as(context).hasSize(35);
    }
  }
}
