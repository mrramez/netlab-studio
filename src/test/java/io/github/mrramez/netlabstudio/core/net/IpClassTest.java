package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class IpClassTest {

  @ParameterizedTest
  @CsvSource({
    "0.0.0.0, A",
    "1.0.0.0, A",
    "10.61.219.43, A",
    "126.255.255.255, A",
    "127.0.0.1, A",
    "127.255.255.255, A",
    "128.0.0.0, B",
    "172.16.254.1, B",
    "191.255.255.255, B",
    "192.0.0.0, C",
    "192.168.1.45, C",
    "223.255.255.255, C",
    "224.0.0.0, D",
    "239.255.255.255, D",
    "240.0.0.0, E",
    "255.255.255.254, E",
    "255.255.255.255, E"
  })
  void classifiesByTheLeadingBitsOfTheFirstOctet(String address, IpClass expected) {
    assertThat(IpClass.of(Ipv4Address.parse(address))).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"A, 8", "B, 16", "C, 24"})
  void unicastClassesHaveADefaultMask(IpClass ipClass, int prefix) {
    assertThat(ipClass.defaultMask()).contains(SubnetMask.ofPrefix(prefix));
  }

  @ParameterizedTest
  @EnumSource(
      value = IpClass.class,
      names = {"D", "E"})
  void multicastAndReservedClassesHaveNoDefaultMask(IpClass ipClass) {
    assertThat(ipClass.defaultMask()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "A, 0, 127, 0",
    "B, 128, 191, 10",
    "C, 192, 223, 110",
    "D, 224, 239, 1110",
    "E, 240, 255, 1111"
  })
  void describesTheFirstOctetRangeAndLeadingBits(IpClass ipClass, int low, int high, String bits) {
    assertThat(ipClass.firstOctetLow()).isEqualTo(low);
    assertThat(ipClass.firstOctetHigh()).isEqualTo(high);
    assertThat(ipClass.leadingBits()).isEqualTo(bits);
    // The leading bits really are the start of every first octet in the range.
    String lowBinary = BinaryConverter.octetToBinary(low);
    String highBinary = BinaryConverter.octetToBinary(high);
    assertThat(lowBinary).startsWith(bits);
    assertThat(highBinary).startsWith(bits);
  }

  @Test
  void rangesCoverEveryFirstOctetExactlyOnce() {
    IpClass[] classes = IpClass.values();
    assertThat(classes[0].firstOctetLow()).isZero();
    assertThat(classes[classes.length - 1].firstOctetHigh()).isEqualTo(255);
    for (int i = 1; i < classes.length; i++) {
      assertThat(classes[i].firstOctetLow()).isEqualTo(classes[i - 1].firstOctetHigh() + 1);
    }
    for (int octet = 0; octet <= 255; octet++) {
      IpClass ipClass = IpClass.of(Ipv4Address.of(octet, 0, 0, 1));
      assertThat(octet).isBetween(ipClass.firstOctetLow(), ipClass.firstOctetHigh());
    }
  }

  @Test
  void flagsTheKindOfAddressEachClassHolds() {
    assertThat(IpClass.A.isUnicast()).isTrue();
    assertThat(IpClass.B.isUnicast()).isTrue();
    assertThat(IpClass.C.isUnicast()).isTrue();
    assertThat(IpClass.D.isUnicast()).isFalse();
    assertThat(IpClass.E.isUnicast()).isFalse();

    assertThat(IpClass.D.isMulticast()).isTrue();
    assertThat(IpClass.A.isMulticast()).isFalse();

    assertThat(IpClass.E.isReserved()).isTrue();
    assertThat(IpClass.D.isReserved()).isFalse();
    assertThat(IpClass.C.isReserved()).isFalse();
  }

  @Test
  void eachUnicastClassHasOnePrivateRange() {
    assertThat(IpClass.A.privateRange()).contains(Ipv4Network.parse("10.0.0.0/8"));
    assertThat(IpClass.B.privateRange()).contains(Ipv4Network.parse("172.16.0.0/12"));
    assertThat(IpClass.C.privateRange()).contains(Ipv4Network.parse("192.168.0.0/16"));
    assertThat(IpClass.D.privateRange()).isEmpty();
    assertThat(IpClass.E.privateRange()).isEmpty();
  }

  @Test
  void privateRangesAgreeWithTheAddressFlag() {
    for (IpClass ipClass : IpClass.values()) {
      Optional<Ipv4Network> range = ipClass.privateRange();
      range.ifPresent(
          network -> {
            assertThat(network.network().isPrivate()).isTrue();
            assertThat(network.broadcast().isPrivate()).isTrue();
            assertThat(network.network().plus(-1).isPrivate()).isFalse();
            assertThat(network.broadcast().plus(1).isPrivate()).isFalse();
          });
    }
  }

  @Test
  void onlyClassAHoldsTheLoopbackRange() {
    assertThat(IpClass.A.loopbackRange()).contains(Ipv4Network.parse("127.0.0.0/8"));
    assertThat(IpClass.B.loopbackRange()).isEmpty();
    assertThat(IpClass.C.loopbackRange()).isEmpty();
    assertThat(IpClass.D.loopbackRange()).isEmpty();
    assertThat(IpClass.E.loopbackRange()).isEmpty();
  }

  @Test
  void anAddressKnowsItsClassThroughItsNetwork() {
    assertThat(Ipv4Network.parse("10.0.0.0/8").ipClass()).isEqualTo(IpClass.A);
    assertThat(Ipv4Network.parse("172.16.0.0/12").ipClass()).isEqualTo(IpClass.B);
    assertThat(Ipv4Network.parse("192.168.1.0/24").ipClass()).isEqualTo(IpClass.C);
  }
}
