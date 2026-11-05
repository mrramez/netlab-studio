package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class Ipv4NetworkTest {

  private static final long SEED = 20260924L;

  private static Ipv4Address ip(String text) {
    return Ipv4Address.parse(text);
  }

  @ParameterizedTest
  @CsvSource({
    // network, prefix, broadcast, first, last, total, usable
    "0.0.0.0, 0, 255.255.255.255, 0.0.0.1, 255.255.255.254, 4294967296, 4294967294",
    "10.0.0.0, 8, 10.255.255.255, 10.0.0.1, 10.255.255.254, 16777216, 16777214",
    "192.168.1.0, 24, 192.168.1.255, 192.168.1.1, 192.168.1.254, 256, 254",
    "192.168.1.64, 26, 192.168.1.127, 192.168.1.65, 192.168.1.126, 64, 62",
    "192.168.1.8, 29, 192.168.1.15, 192.168.1.9, 192.168.1.14, 8, 6",
    "10.0.0.4, 30, 10.0.0.7, 10.0.0.5, 10.0.0.6, 4, 2",
    "192.168.1.0, 31, 192.168.1.1, 192.168.1.0, 192.168.1.1, 2, 2",
    "192.168.1.5, 32, 192.168.1.5, 192.168.1.5, 192.168.1.5, 1, 1"
  })
  void derivesTheAddressesOfANetwork(
      String network,
      int prefix,
      String broadcast,
      String first,
      String last,
      long total,
      long usable) {
    Ipv4Network net = new Ipv4Network(ip(network), SubnetMask.ofPrefix(prefix));

    assertThat(net.broadcast()).isEqualTo(ip(broadcast));
    assertThat(net.firstUsable()).isEqualTo(ip(first));
    assertThat(net.lastUsable()).isEqualTo(ip(last));
    assertThat(net.totalAddresses()).isEqualTo(total);
    assertThat(net.usableHostCount()).isEqualTo(usable);
    assertThat(net.prefixLength()).isEqualTo(prefix);
  }

  @Test
  void aSlash31HasTwoUsableAddressesAsPointToPointLinksUseThem() {
    // RFC 3021: no network or broadcast address is reserved on a point-to-point /31.
    Ipv4Network net = Ipv4Network.parse("10.0.0.0/31");

    assertThat(net.usableHostCount()).isEqualTo(2);
    assertThat(net.firstUsable()).isEqualTo(net.network());
    assertThat(net.lastUsable()).isEqualTo(net.broadcast());
  }

  @Test
  void aSlash32IsASingleHost() {
    Ipv4Network net = Ipv4Network.parse("10.1.2.3/32");

    assertThat(net.usableHostCount()).isEqualTo(1);
    assertThat(net.network()).isEqualTo(net.broadcast());
    assertThat(net.firstUsable()).isEqualTo(net.lastUsable()).isEqualTo(ip("10.1.2.3"));
  }

  @ParameterizedTest
  @CsvSource({
    // network, prefix, interesting octet, block size
    "0.0.0.0, 0, 1, 256",
    "10.0.0.0, 8, 1, 1",
    "10.48.0.0, 12, 2, 16",
    "172.16.0.0, 16, 2, 1",
    "200.90.0.0, 17, 3, 128",
    "100.10.64.0, 20, 3, 16",
    "192.168.1.0, 24, 3, 1",
    "192.168.1.64, 26, 4, 64",
    "192.14.2.32, 27, 4, 32",
    "192.168.1.8, 29, 4, 8",
    "192.168.1.4, 30, 4, 4",
    "192.168.1.5, 32, 4, 1"
  })
  void blockSizeIsInTheOctetWhereTheNetworkPartEnds(
      String network, int prefix, int octet, int blockSize) {
    Ipv4Network net = new Ipv4Network(ip(network), SubnetMask.ofPrefix(prefix));

    assertThat(net.interestingOctet()).isEqualTo(octet);
    assertThat(net.blockSize()).isEqualTo(blockSize);
  }

  @Test
  void containsIncludesTheNetworkAndBroadcastAddressesButNothingBeyond() {
    Ipv4Network net = Ipv4Network.parse("192.168.1.64/26");

    assertThat(net.contains(ip("192.168.1.64"))).isTrue();
    assertThat(net.contains(ip("192.168.1.100"))).isTrue();
    assertThat(net.contains(ip("192.168.1.127"))).isTrue();
    assertThat(net.contains(ip("192.168.1.63"))).isFalse();
    assertThat(net.contains(ip("192.168.1.128"))).isFalse();
    assertThat(net.contains(ip("10.0.0.1"))).isFalse();
  }

  @Test
  void containsWorksAtTheEdgesOfTheAddressSpace() {
    Ipv4Network everything = Ipv4Network.parse("0.0.0.0/0");
    Ipv4Network top = Ipv4Network.parse("255.255.255.0/24");

    assertThat(everything.contains(ip("0.0.0.0"))).isTrue();
    assertThat(everything.contains(ip("255.255.255.255"))).isTrue();
    assertThat(top.contains(ip("255.255.255.255"))).isTrue();
    assertThat(top.contains(ip("255.255.254.255"))).isFalse();
  }

  @Test
  void containsAnotherNetworkOnlyWhenItIsInsideOrEqual() {
    Ipv4Network big = Ipv4Network.parse("10.0.0.0/8");
    Ipv4Network inside = Ipv4Network.parse("10.1.0.0/16");
    Ipv4Network elsewhere = Ipv4Network.parse("11.0.0.0/16");

    assertThat(big.contains(inside)).isTrue();
    assertThat(big.contains(big)).isTrue();
    assertThat(inside.contains(big)).isFalse();
    assertThat(big.contains(elsewhere)).isFalse();
  }

  @Test
  void overlapsWhenOneContainsTheOther() {
    Ipv4Network big = Ipv4Network.parse("10.0.0.0/8");
    Ipv4Network inside = Ipv4Network.parse("10.1.0.0/16");
    Ipv4Network elsewhere = Ipv4Network.parse("11.0.0.0/16");

    assertThat(big.overlaps(inside)).isTrue();
    assertThat(inside.overlaps(big)).isTrue();
    assertThat(big.overlaps(elsewhere)).isFalse();
    assertThat(elsewhere.overlaps(big)).isFalse();
    assertThat(inside.overlaps(elsewhere)).isFalse();
  }

  @Test
  void theNetworkConstructorRejectsHostBits() {
    assertThatThrownBy(() -> new Ipv4Network(ip("10.0.0.5"), SubnetMask.ofPrefix(24)))
        .isInstanceOf(InvalidAddressException.class)
        .hasMessageContaining("host bits")
        .hasMessageContaining("10.0.0.0/24");
  }

  @Test
  void containingZeroesTheHostBits() {
    Ipv4Network net = Ipv4Network.containing(ip("10.61.219.43"), SubnetMask.ofPrefix(12));

    assertThat(net.network()).isEqualTo(ip("10.48.0.0"));
    assertThat(net).hasToString("10.48.0.0/12");
  }

  @Test
  void parseReadsAHostAddressWithAPrefixAndFindsItsNetwork() {
    Ipv4Network net = Ipv4Network.parse("192.16.254.1/24");

    assertThat(net.network()).isEqualTo(ip("192.16.254.0"));
    assertThat(net.mask()).isEqualTo(SubnetMask.ofPrefix(24));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "10.0.0.0",
        "10.0.0.0/",
        "10.0.0.0/33",
        "10.0.0.0/24/8",
        "/24",
        "10.0.0/24",
        "10.0.0.0/ 24",
        "10.0.0.0/-1",
        "300.0.0.0/8",
        "10.0.0.0/255.0.0.0",
        "10.0.0.0/24 ",
        "1111111111111111111111111111/8"
      })
  void parseRejectsMalformedText(String text) {
    assertThatThrownBy(() -> Ipv4Network.parse(text)).isInstanceOf(InvalidAddressException.class);
  }

  @Test
  void aMissingSlashSaysHowToWriteIt() {
    assertThatThrownBy(() -> Ipv4Network.parse("10.0.0.0")).hasMessageContaining("address/prefix");
  }

  @Test
  void rejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> Ipv4Network.parse(null));
    assertThatNullPointerException()
        .isThrownBy(() -> new Ipv4Network(null, SubnetMask.ofPrefix(8)));
    assertThatNullPointerException().isThrownBy(() -> new Ipv4Network(ip("10.0.0.0"), null));
    assertThatNullPointerException()
        .isThrownBy(() -> Ipv4Network.containing(null, SubnetMask.ofPrefix(8)));
    Ipv4Network net = Ipv4Network.parse("10.0.0.0/8");
    assertThatNullPointerException().isThrownBy(() -> net.contains((Ipv4Address) null));
    assertThatNullPointerException().isThrownBy(() -> net.contains((Ipv4Network) null));
    assertThatNullPointerException().isThrownBy(() -> net.overlaps(null));
  }

  @Test
  void nextNetworkIsTheAdjacentBlockOfTheSameSize() {
    assertThat(Ipv4Network.parse("192.14.2.0/27").nextNetwork())
        .isEqualTo(Ipv4Network.parse("192.14.2.32/27"));
    assertThat(Ipv4Network.parse("192.14.2.224/27").nextNetwork())
        .isEqualTo(Ipv4Network.parse("192.14.3.0/27"));
    assertThat(Ipv4Network.parse("10.0.0.0/12").nextNetwork())
        .isEqualTo(Ipv4Network.parse("10.16.0.0/12"));
  }

  @Test
  void nextNetworkFailsAtTheEndOfTheAddressSpace() {
    assertThatThrownBy(() -> Ipv4Network.parse("255.255.255.0/24").nextNetwork())
        .isInstanceOf(InvalidAddressException.class);
    assertThatThrownBy(() -> Ipv4Network.parse("0.0.0.0/0").nextNetwork())
        .isInstanceOf(InvalidAddressException.class);
  }

  @Test
  void wildcardIsTheInverseOfTheMask() {
    assertThat(Ipv4Network.parse("10.48.0.0/12").wildcard()).isEqualTo(ip("0.15.255.255"));
  }

  @Test
  void printsInSlashNotation() {
    assertThat(Ipv4Network.parse("172.16.254.1/16")).hasToString("172.16.0.0/16");
  }

  @Test
  void invariantsHoldForRandomHostsAndPrefixes() {
    Random random = new Random(SEED);
    for (int i = 0; i < 10_000; i++) {
      Ipv4Address host = new Ipv4Address(random.nextInt());
      SubnetMask mask = SubnetMask.ofPrefix(random.nextInt(33));
      Ipv4Network net = Ipv4Network.containing(host, mask);
      String context = "seed " + SEED + ", iteration " + i + ", " + host + mask.toCidrString();

      // network <= host <= broadcast, compared as unsigned numbers.
      assertThat(net.network().compareTo(host)).as(context).isLessThanOrEqualTo(0);
      assertThat(host.compareTo(net.broadcast())).as(context).isLessThanOrEqualTo(0);
      assertThat(net.contains(host)).as(context).isTrue();

      assertThat(net.network()).as(context).isEqualTo(host.and(mask));
      assertThat(net.broadcast().value()).as(context).isEqualTo(host.value() | ~mask.toInt());
      assertThat(net.broadcast().toUnsignedLong() - net.network().toUnsignedLong() + 1)
          .as(context)
          .isEqualTo(net.totalAddresses());

      long firstToLast = net.lastUsable().toUnsignedLong() - net.firstUsable().toUnsignedLong() + 1;
      assertThat(net.usableHostCount()).as(context).isEqualTo(firstToLast);
      assertThat(net.contains(net.firstUsable())).as(context).isTrue();
      assertThat(net.contains(net.lastUsable())).as(context).isTrue();

      // An address just outside the block is not contained.
      if (net.network().toUnsignedLong() > 0) {
        assertThat(net.contains(net.network().plus(-1))).as(context).isFalse();
      }
      if (net.broadcast().toUnsignedLong() < 0xFFFF_FFFFL) {
        assertThat(net.contains(net.broadcast().plus(1))).as(context).isFalse();
      }

      assertThat(Ipv4Network.parse(net.toString())).as(context).isEqualTo(net);

      // The network address is a multiple of the block size in the interesting octet.
      int octet = net.network().octet(net.interestingOctet());
      assertThat(octet % net.blockSize()).as(context).isZero();
      assertThat(Integer.bitCount(net.blockSize())).as(context).isOne();
    }
  }
}
