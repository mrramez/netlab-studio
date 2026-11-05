package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SubnetCalculatorTest {

  private static final long SEED = 20260924L;

  private static Ipv4Address ip(String text) {
    return Ipv4Address.parse(text);
  }

  private static Ipv4Network net(String text) {
    return Ipv4Network.parse(text);
  }

  private static List<String> keys(Explained<?> explained) {
    return explained.steps().stream().map(ExplanationStep::key).toList();
  }

  // ---------------------------------------------------------------- by number of subnets

  @ParameterizedTest(name = "{0} needing {1} subnets -> /{2}")
  @CsvSource({
    // base, required subnets, new prefix, borrowed bits, subnets, hosts per subnet
    "192.14.2.0/24, 6, 27, 3, 8, 30",
    "192.168.1.0/24, 1, 24, 0, 1, 254",
    "192.168.1.0/24, 2, 25, 1, 2, 126",
    "192.168.1.0/24, 3, 26, 2, 4, 62",
    "192.168.1.0/24, 4, 26, 2, 4, 62",
    "192.168.1.0/24, 5, 27, 3, 8, 30",
    "192.168.1.0/24, 8, 27, 3, 8, 30",
    "192.168.1.0/24, 9, 28, 4, 16, 14",
    "192.168.1.0/24, 64, 30, 6, 64, 2",
    "172.16.0.0/16, 100, 23, 7, 128, 510",
    "10.0.0.0/8, 16, 12, 4, 16, 1048574",
    "10.0.0.0/8, 1000, 18, 10, 1024, 16382",
    "192.168.1.0/30, 1, 30, 0, 1, 2"
  })
  void bySubnetCountBorrowsTheSmallestNumberOfBits(
      String base, int required, int prefix, int borrowed, long subnets, long hosts) {
    SubnetPlan plan = SubnetCalculator.bySubnetCount(net(base), required).result();

    assertThat(plan.base()).isEqualTo(net(base));
    assertThat(plan.subnetMask()).isEqualTo(SubnetMask.ofPrefix(prefix));
    assertThat(plan.borrowedBits()).isEqualTo(borrowed);
    assertThat(plan.subnetCount()).isEqualTo(subnets);
    assertThat(plan.hostsPerSubnet()).isEqualTo(hosts);
    assertThat(plan.hostBits()).isEqualTo(32 - prefix);
  }

  @Test
  void aPlanKnowsItsBlockSizeAndInterestingOctet() {
    SubnetPlan plan = SubnetCalculator.bySubnetCount(net("192.14.2.0/24"), 6).result();

    assertThat(plan.blockSize()).isEqualTo(32);
    assertThat(plan.interestingOctet()).isEqualTo(4);
  }

  @Test
  void bySubnetCountExplainsEachStep() {
    Explained<SubnetPlan> explained = SubnetCalculator.bySubnetCount(net("192.14.2.0/24"), 6);

    assertThat(keys(explained))
        .containsExactly(
            "net.plan.base",
            "net.plan.borrowForSubnets",
            "net.plan.newMask",
            "net.plan.hostsPerSubnet",
            "net.plan.blockSize",
            "net.plan.subnetZeroNote");
    assertThat(explained.steps().get(0).args()).containsExactly("192.14.2.0/24");
    assertThat(explained.steps().get(1).args()).containsExactly(6, 3, 8L);
    assertThat(explained.steps().get(2).args()).containsExactly(24, 3, 27, "255.255.255.224");
    assertThat(explained.steps().get(3).args()).containsExactly(5, 30L);
    assertThat(explained.steps().get(4).args()).containsExactly(4, 224, 32);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
  void bySubnetCountNeedsAtLeastOneSubnet(int required) {
    assertThatThrownBy(() -> SubnetCalculator.bySubnetCount(net("192.168.1.0/24"), required))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 1");
  }

  @ParameterizedTest
  @CsvSource({
    "192.168.1.0/24, 65",
    "192.168.1.0/24, 1000",
    "192.168.1.0/24, 2147483647",
    "192.168.1.0/30, 2",
    "192.168.1.0/31, 1",
    "192.168.1.1/32, 1"
  })
  void bySubnetCountRefusesToLeaveLessThanTwoHostBits(String base, int required) {
    assertThatThrownBy(() -> SubnetCalculator.bySubnetCount(net(base), required))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("/30");
  }

  @ParameterizedTest
  @CsvSource({
    "192.14.2.0, 6, 27",
    "192.14.2.77, 6, 27",
    "172.16.5.9, 100, 23",
    "10.0.0.0, 16, 12",
    "10.200.3.4, 1, 8"
  })
  void bySubnetCountFromAnAddressStartsFromTheClassfulNetwork(
      String address, int required, int prefix) {
    Explained<SubnetPlan> explained = SubnetCalculator.bySubnetCount(ip(address), required);

    assertThat(explained.result().subnetMask()).isEqualTo(SubnetMask.ofPrefix(prefix));
    assertThat(explained.result().base().network().and(SubnetMask.ofPrefix(8)))
        .isEqualTo(ip(address).and(SubnetMask.ofPrefix(8)));
    assertThat(keys(explained).get(0)).isEqualTo("net.plan.classfulBase");
  }

  @Test
  void theClassfulBaseStepNamesTheClassAndMask() {
    Explained<SubnetPlan> explained = SubnetCalculator.bySubnetCount(ip("192.14.2.0"), 6);

    assertThat(explained.steps().get(0).args())
        .containsExactly("192.14.2.0", "C", "255.255.255.0", "192.14.2.0/24");
  }

  @ParameterizedTest
  @ValueSource(strings = {"224.0.0.1", "239.255.255.255", "240.0.0.1", "255.255.255.255"})
  void fromAnAddressTheClassMustHaveADefaultMask(String address) {
    assertThatThrownBy(() -> SubnetCalculator.bySubnetCount(ip(address), 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("class");
    assertThatThrownBy(() -> SubnetCalculator.byHostCount(ip(address), 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("class");
  }

  // ---------------------------------------------------------------- by hosts per subnet

  @ParameterizedTest(name = "{0} needing {1} hosts -> /{2}")
  @CsvSource({
    // base, required hosts, new prefix, borrowed bits, subnets, hosts per subnet
    "192.168.1.0/24, 1, 30, 6, 64, 2",
    "192.168.1.0/24, 2, 30, 6, 64, 2",
    "192.168.1.0/24, 3, 29, 5, 32, 6",
    "192.168.1.0/24, 6, 29, 5, 32, 6",
    "192.168.1.0/24, 7, 28, 4, 16, 14",
    "192.168.1.0/24, 30, 27, 3, 8, 30",
    "192.168.1.0/24, 31, 26, 2, 4, 62",
    "192.168.1.0/24, 126, 25, 1, 2, 126",
    "192.168.1.0/24, 127, 24, 0, 1, 254",
    "192.168.1.0/24, 254, 24, 0, 1, 254",
    "172.16.0.0/16, 500, 23, 7, 128, 510",
    "10.0.0.0/8, 1000000, 12, 4, 16, 1048574",
    "10.0.0.0/30, 2, 30, 0, 1, 2"
  })
  void byHostCountKeepsTheSmallestNumberOfHostBits(
      String base, int required, int prefix, int borrowed, long subnets, long hosts) {
    SubnetPlan plan = SubnetCalculator.byHostCount(net(base), required).result();

    assertThat(plan.subnetMask()).isEqualTo(SubnetMask.ofPrefix(prefix));
    assertThat(plan.borrowedBits()).isEqualTo(borrowed);
    assertThat(plan.subnetCount()).isEqualTo(subnets);
    assertThat(plan.hostsPerSubnet()).isEqualTo(hosts);
  }

  @Test
  void byHostCountExplainsEachStep() {
    Explained<SubnetPlan> explained = SubnetCalculator.byHostCount(net("192.14.2.0/24"), 25);

    assertThat(keys(explained))
        .containsExactly(
            "net.plan.base",
            "net.plan.hostBitsNeeded",
            "net.plan.newMaskFromHosts",
            "net.plan.borrowedBits",
            "net.plan.blockSize",
            "net.plan.subnetZeroNote");
    assertThat(explained.steps().get(1).args()).containsExactly(25, 5, 30L);
    assertThat(explained.steps().get(2).args()).containsExactly(5, 27, "255.255.255.224");
    assertThat(explained.steps().get(3).args()).containsExactly(24, 27, 3, 8L);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -5, Integer.MIN_VALUE})
  void byHostCountNeedsAtLeastOneHost(int required) {
    assertThatThrownBy(() -> SubnetCalculator.byHostCount(net("192.168.1.0/24"), required))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 1");
  }

  @ParameterizedTest
  @CsvSource({
    "192.168.1.0/24, 255",
    "192.168.1.0/24, 1000",
    "192.168.1.0/24, 2147483647",
    "192.168.1.0/31, 1",
    "192.168.1.1/32, 1"
  })
  void byHostCountRefusesMoreHostsThanTheBaseCanHold(String base, int required) {
    assertThatThrownBy(() -> SubnetCalculator.byHostCount(net(base), required))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("host bits");
  }

  @Test
  void byHostCountFromAnAddressStartsFromTheClassfulNetwork() {
    Explained<SubnetPlan> explained = SubnetCalculator.byHostCount(ip("172.16.5.9"), 500);

    assertThat(explained.result().base()).isEqualTo(net("172.16.0.0/16"));
    assertThat(explained.result().subnetMask()).isEqualTo(SubnetMask.ofPrefix(23));
    assertThat(keys(explained).get(0)).isEqualTo("net.plan.classfulBase");
  }

  @Test
  void plansAreMinimalForRandomRequirements() {
    Random random = new Random(SEED);
    for (int i = 0; i < 5_000; i++) {
      int basePrefix = 8 + random.nextInt(17);
      Ipv4Network base =
          Ipv4Network.containing(
              new Ipv4Address(random.nextInt()), SubnetMask.ofPrefix(basePrefix));
      String context = "seed " + SEED + ", iteration " + i + ", base " + base;

      int maxSubnets = 1 << (30 - basePrefix);
      int subnets = 1 + random.nextInt(maxSubnets);
      SubnetPlan bySubnets = SubnetCalculator.bySubnetCount(base, subnets).result();
      assertThat(bySubnets.subnetCount()).as(context).isGreaterThanOrEqualTo(subnets);
      // One bit fewer would not give enough subnets.
      if (bySubnets.borrowedBits() > 0) {
        assertThat(bySubnets.subnetCount() / 2).as(context).isLessThan(subnets);
      }
      assertThat(bySubnets.hostsPerSubnet()).as(context).isGreaterThanOrEqualTo(2);

      int maxHosts = (int) (base.totalAddresses() - 2);
      int hosts = 1 + random.nextInt(maxHosts);
      SubnetPlan byHosts = SubnetCalculator.byHostCount(base, hosts).result();
      assertThat(byHosts.hostsPerSubnet()).as(context).isGreaterThanOrEqualTo(hosts);
      // One host bit fewer would not hold enough hosts (the smallest subnet is a /30).
      if (byHosts.hostBits() > 2) {
        assertThat((1L << (byHosts.hostBits() - 1)) - 2).as(context).isLessThan(hosts);
      }
      assertThat(byHosts.subnetMask().prefixLength())
          .as(context)
          .isGreaterThanOrEqualTo(basePrefix);
    }
  }

  // ---------------------------------------------------------------- listing subnets

  @Test
  void listsTheFirstSubnetsInOrder() {
    Explained<List<Ipv4Network>> listed =
        SubnetCalculator.listSubnets(net("192.14.2.0/24"), SubnetMask.ofPrefix(27), 3);

    assertThat(listed.result())
        .containsExactly(net("192.14.2.0/27"), net("192.14.2.32/27"), net("192.14.2.64/27"));
  }

  @Test
  void listingExplainsTheBlockSizeAndEverySubnet() {
    Explained<List<Ipv4Network>> listed =
        SubnetCalculator.listSubnets(net("192.14.2.0/24"), SubnetMask.ofPrefix(27), 3);

    assertThat(keys(listed))
        .containsExactly("net.list.start", "net.list.subnet", "net.list.subnet", "net.list.subnet");
    assertThat(listed.steps().get(0).args()).containsExactly("192.14.2.0/24", "/27", 4, 32);
    assertThat(listed.steps().get(1).args())
        .containsExactly(1, "192.14.2.0/27", "192.14.2.1", "192.14.2.30", "192.14.2.31");
    assertThat(listed.steps().get(3).args())
        .containsExactly(3, "192.14.2.64/27", "192.14.2.65", "192.14.2.94", "192.14.2.95");
  }

  @Test
  void listingStopsAtTheLastSubnetAndSaysSo() {
    Explained<List<Ipv4Network>> listed =
        SubnetCalculator.listSubnets(net("192.168.1.0/24"), SubnetMask.ofPrefix(26), 10);

    assertThat(listed.result())
        .containsExactly(
            net("192.168.1.0/26"),
            net("192.168.1.64/26"),
            net("192.168.1.128/26"),
            net("192.168.1.192/26"));
    assertThat(keys(listed)).contains("net.list.limited");
    assertThat(
            listed.steps().stream()
                .filter(step -> step.key().equals("net.list.limited"))
                .findFirst()
                .orElseThrow()
                .args())
        .containsExactly(10, 4L);
  }

  @Test
  void listingWithTheSameMaskGivesTheBaseItself() {
    assertThat(SubnetCalculator.listSubnets(net("10.0.0.0/8"), SubnetMask.ofPrefix(8), 5).result())
        .containsExactly(net("10.0.0.0/8"));
  }

  @Test
  void listingCrossesOctetBoundaries() {
    assertThat(SubnetCalculator.listSubnets(net("10.0.0.0/8"), SubnetMask.ofPrefix(12), 3).result())
        .containsExactly(net("10.0.0.0/12"), net("10.16.0.0/12"), net("10.32.0.0/12"));
    assertThat(
            SubnetCalculator.listSubnets(net("172.16.0.0/16"), SubnetMask.ofPrefix(24), 3).result())
        .containsExactly(net("172.16.0.0/24"), net("172.16.1.0/24"), net("172.16.2.0/24"));
  }

  @Test
  void listingRefusesAMaskShorterThanTheBase() {
    assertThatThrownBy(
            () -> SubnetCalculator.listSubnets(net("192.168.1.0/24"), SubnetMask.ofPrefix(23), 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("shorter");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 1025, Integer.MAX_VALUE})
  void listingRefusesACountOutsideOneToAThousandAndTwentyFour(int count) {
    assertThatThrownBy(
            () -> SubnetCalculator.listSubnets(net("10.0.0.0/8"), SubnetMask.ofPrefix(24), count))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and 1024");
  }

  @Test
  void listsAThousandAndTwentyFourSubnets() {
    assertThat(
            SubnetCalculator.listSubnets(net("10.0.0.0/8"), SubnetMask.ofPrefix(24), 1024).result())
        .hasSize(1024);
  }

  @Test
  void listedSubnetsAreAdjacentAlignedAndInsideTheBase() {
    Random random = new Random(SEED);
    for (int i = 0; i < 500; i++) {
      int basePrefix = random.nextInt(25);
      int newPrefix = basePrefix + random.nextInt(33 - basePrefix);
      Ipv4Network base =
          Ipv4Network.containing(
              new Ipv4Address(random.nextInt()), SubnetMask.ofPrefix(basePrefix));
      String context = "seed " + SEED + ", iteration " + i + ", " + base + " -> /" + newPrefix;

      List<Ipv4Network> subnets =
          SubnetCalculator.listSubnets(base, SubnetMask.ofPrefix(newPrefix), 50).result();

      assertThat(subnets).as(context).isNotEmpty();
      assertThat(subnets.get(0).network()).as(context).isEqualTo(base.network());
      List<Ipv4Network> seen = new ArrayList<>();
      for (Ipv4Network subnet : subnets) {
        assertThat(base.contains(subnet)).as(context).isTrue();
        assertThat(subnet.prefixLength()).as(context).isEqualTo(newPrefix);
        for (Ipv4Network earlier : seen) {
          assertThat(subnet.overlaps(earlier)).as(context).isFalse();
        }
        if (!seen.isEmpty()) {
          Ipv4Network previous = seen.get(seen.size() - 1);
          assertThat(subnet.network().toUnsignedLong())
              .as(context)
              .isEqualTo(previous.broadcast().toUnsignedLong() + 1);
        }
        seen.add(subnet);
      }
    }
  }

  // ---------------------------------------------------------------- which subnet is a host in

  @Test
  void subnetOfFindsTheNetworkByAndingTheHostWithTheMask() {
    Explained<Ipv4Network> explained =
        SubnetCalculator.subnetOf(ip("10.61.219.43"), SubnetMask.ofPrefix(12));

    assertThat(explained.result()).isEqualTo(net("10.48.0.0/12"));
  }

  @Test
  void subnetOfShowsTheBinaryWorkAndTheResultingRange() {
    Explained<Ipv4Network> explained =
        SubnetCalculator.subnetOf(ip("192.168.1.77"), SubnetMask.ofPrefix(26));

    assertThat(keys(explained))
        .containsExactly(
            "net.subnet.hostBinary",
            "net.subnet.maskBinary",
            "net.subnet.andResult",
            "net.subnet.broadcast",
            "net.subnet.range",
            "net.subnet.blockSize");
    assertThat(explained.steps().get(0).args())
        .containsExactly("192.168.1.77", "11000000.10101000.00000001.01001101", 26);
    assertThat(explained.steps().get(1).args())
        .containsExactly("255.255.255.192", "11111111.11111111.11111111.11000000", 26);
    assertThat(explained.steps().get(2).args())
        .containsExactly("11000000.10101000.00000001.01000000", "192.168.1.64", 26);
    assertThat(explained.steps().get(3).args())
        .containsExactly("11000000.10101000.00000001.01111111", "192.168.1.127", 6);
    assertThat(explained.steps().get(4).args())
        .containsExactly("192.168.1.65", "192.168.1.126", 62L);
    assertThat(explained.steps().get(5).args()).containsExactly(4, 192, 64);
  }

  @Test
  void subnetOfAPointToPointLinkMentionsRfc3021() {
    Explained<Ipv4Network> explained =
        SubnetCalculator.subnetOf(ip("10.0.0.1"), SubnetMask.ofPrefix(31));

    assertThat(explained.result()).isEqualTo(net("10.0.0.0/31"));
    assertThat(keys(explained))
        .contains("net.subnet.pointToPoint")
        .doesNotContain("net.subnet.singleHost");
  }

  @Test
  void subnetOfASingleHostSaysSo() {
    Explained<Ipv4Network> explained =
        SubnetCalculator.subnetOf(ip("10.0.0.1"), SubnetMask.ofPrefix(32));

    assertThat(explained.result()).isEqualTo(net("10.0.0.1/32"));
    assertThat(keys(explained))
        .contains("net.subnet.singleHost")
        .doesNotContain("net.subnet.pointToPoint");
  }

  @Test
  void subnetOfAgreesWithIpv4NetworkContainingForRandomInput() {
    Random random = new Random(SEED);
    for (int i = 0; i < 5_000; i++) {
      Ipv4Address host = new Ipv4Address(random.nextInt());
      SubnetMask mask = SubnetMask.ofPrefix(random.nextInt(33));
      String context = "seed " + SEED + ", iteration " + i + ", " + host + mask.toCidrString();

      Ipv4Network found = SubnetCalculator.subnetOf(host, mask).result();

      assertThat(found).as(context).isEqualTo(Ipv4Network.containing(host, mask));
      assertThat(found.contains(host)).as(context).isTrue();
    }
  }

  // ---------------------------------------------------------------- classful counts

  @ParameterizedTest(name = "{0}{1} -> {2} borrowed bits, {3} subnets, {5} hosts")
  @CsvSource({
    // address, mask, borrowed, subnets, host bits, hosts per subnet
    "10.0.0.1, /8, 0, 1, 24, 16777214",
    "10.61.219.43, /12, 4, 16, 20, 1048574",
    "172.16.254.1, /16, 0, 1, 16, 65534",
    "172.16.254.1, /24, 8, 256, 8, 254",
    "192.168.1.1, /24, 0, 1, 8, 254",
    "192.168.1.1, /27, 3, 8, 5, 30",
    "192.168.1.1, /29, 5, 32, 3, 6",
    "192.168.1.1, /30, 6, 64, 2, 2",
    "100.10.70.0, /20, 12, 4096, 12, 4094"
  })
  void classfulCountsUseTwoToTheSAndTwoToTheHMinusTwo(
      String address, String mask, int borrowed, long subnets, int hostBits, long hosts) {
    ClassfulCounts counts =
        SubnetCalculator.classfulCounts(ip(address), SubnetMask.parse(mask)).result();

    assertThat(counts.borrowedBits()).isEqualTo(borrowed);
    assertThat(counts.subnetCount()).isEqualTo(subnets);
    assertThat(counts.hostBits()).isEqualTo(hostBits);
    assertThat(counts.hostsPerSubnet()).isEqualTo(hosts);
    assertThat(counts.isSupernet()).isFalse();
    assertThat(counts.supernetBits()).isZero();
    assertThat(counts.combinedNetworks()).isOne();
    assertThat(counts.mask()).isEqualTo(SubnetMask.parse(mask));
  }

  @Test
  void classfulCountsKnowTheClassAndItsDefaultMask() {
    ClassfulCounts counts =
        SubnetCalculator.classfulCounts(ip("10.61.219.43"), SubnetMask.ofPrefix(12)).result();

    assertThat(counts.ipClass()).isEqualTo(IpClass.A);
    assertThat(counts.defaultMask()).isEqualTo(SubnetMask.ofPrefix(8));
  }

  @Test
  void classfulCountsExplainEachStep() {
    Explained<ClassfulCounts> explained =
        SubnetCalculator.classfulCounts(ip("10.61.219.43"), SubnetMask.ofPrefix(12));

    assertThat(keys(explained))
        .containsExactly(
            "net.classful.class",
            "net.classful.borrowed",
            "net.classful.subnetCount",
            "net.classful.hostsPerSubnet",
            "net.classful.subnetZeroNote");
    assertThat(explained.steps().get(0).args())
        .containsExactly("10.61.219.43", "A", 8, "255.0.0.0");
    assertThat(explained.steps().get(1).args()).containsExactly(12, 8, 4);
    assertThat(explained.steps().get(2).args()).containsExactly(4, 16L);
    assertThat(explained.steps().get(3).args()).containsExactly(20, 1_048_574L);
  }

  @Test
  void aMaskShorterThanTheClassDefaultIsASupernet() {
    Explained<ClassfulCounts> explained =
        SubnetCalculator.classfulCounts(ip("200.90.0.0"), SubnetMask.ofPrefix(17));
    ClassfulCounts counts = explained.result();

    assertThat(counts.ipClass()).isEqualTo(IpClass.C);
    assertThat(counts.isSupernet()).isTrue();
    assertThat(counts.supernetBits()).isEqualTo(7);
    assertThat(counts.combinedNetworks()).isEqualTo(128);
    assertThat(counts.borrowedBits()).isZero();
    assertThat(counts.subnetCount()).isOne();
    assertThat(counts.hostBits()).isEqualTo(15);
    assertThat(counts.hostsPerSubnet()).isEqualTo(32_766);
    assertThat(keys(explained))
        .containsExactly(
            "net.classful.class", "net.classful.supernet", "net.classful.hostsPerSubnet");
    assertThat(explained.steps().get(1).args()).containsExactly(17, 24, 7, 128L);
  }

  @Test
  void aSlash31HasNoHostsByTheClassicFormulaButTheStepMentionsRfc3021() {
    Explained<ClassfulCounts> explained =
        SubnetCalculator.classfulCounts(ip("192.168.1.0"), SubnetMask.ofPrefix(31));

    assertThat(explained.result().hostsPerSubnet()).isZero();
    assertThat(explained.result().subnetCount()).isEqualTo(128);
    assertThat(keys(explained)).contains("net.classful.rfc3021");
  }

  @Test
  void aSlash32HasNoHostsByTheClassicFormulaButIsASingleHost() {
    Explained<ClassfulCounts> explained =
        SubnetCalculator.classfulCounts(ip("192.168.1.0"), SubnetMask.ofPrefix(32));

    assertThat(explained.result().hostsPerSubnet()).isZero();
    assertThat(explained.result().subnetCount()).isEqualTo(256);
    assertThat(keys(explained)).contains("net.classful.singleHost");
  }

  @ParameterizedTest
  @ValueSource(strings = {"224.0.0.1", "239.1.1.1", "240.0.0.1", "255.255.255.255"})
  void classfulCountsNeedAClassWithADefaultMask(String address) {
    assertThatThrownBy(() -> SubnetCalculator.classfulCounts(ip(address), SubnetMask.ofPrefix(24)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("class");
  }

  // ---------------------------------------------------------------- nulls

  @Test
  void rejectsNulls() {
    Ipv4Network base = net("192.168.1.0/24");
    SubnetMask mask = SubnetMask.ofPrefix(26);
    Ipv4Address address = ip("192.168.1.1");

    assertThatNullPointerException()
        .isThrownBy(() -> SubnetCalculator.bySubnetCount((Ipv4Network) null, 2));
    assertThatNullPointerException()
        .isThrownBy(() -> SubnetCalculator.bySubnetCount((Ipv4Address) null, 2));
    assertThatNullPointerException()
        .isThrownBy(() -> SubnetCalculator.byHostCount((Ipv4Network) null, 2));
    assertThatNullPointerException()
        .isThrownBy(() -> SubnetCalculator.byHostCount((Ipv4Address) null, 2));
    assertThatNullPointerException().isThrownBy(() -> SubnetCalculator.listSubnets(null, mask, 2));
    assertThatNullPointerException().isThrownBy(() -> SubnetCalculator.listSubnets(base, null, 2));
    assertThatNullPointerException().isThrownBy(() -> SubnetCalculator.subnetOf(null, mask));
    assertThatNullPointerException().isThrownBy(() -> SubnetCalculator.subnetOf(address, null));
    assertThatNullPointerException().isThrownBy(() -> SubnetCalculator.classfulCounts(null, mask));
    assertThatNullPointerException()
        .isThrownBy(() -> SubnetCalculator.classfulCounts(address, null));
  }

  @Test
  void plansRejectInconsistentValues() {
    Ipv4Network base = net("192.168.1.0/24");

    assertThatNullPointerException()
        .isThrownBy(() -> new SubnetPlan(null, SubnetMask.ofPrefix(26), 2, 4, 62));
    assertThatNullPointerException().isThrownBy(() -> new SubnetPlan(base, null, 2, 4, 62));
    assertThatThrownBy(() -> new SubnetPlan(base, SubnetMask.ofPrefix(26), 3, 4, 62))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("borrowed");
  }
}
