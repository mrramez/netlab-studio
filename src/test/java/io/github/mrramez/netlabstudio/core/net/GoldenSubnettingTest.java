package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The golden vectors of docs/CURRICULUM.md section 4.2 (subnetting worksheet and exam). */
class GoldenSubnettingTest {

  private static Ipv4Address ip(String text) {
    return Ipv4Address.parse(text);
  }

  private static Ipv4Network net(String text) {
    return Ipv4Network.parse(text);
  }

  // 4.2 bullet 1: 10.61.219.43 255.240.0.0 (/12, class A).
  @Test
  void bullet1ClassASubnetCounts() {
    SubnetMask mask = SubnetMask.parse("255.240.0.0");
    ClassfulCounts counts = SubnetCalculator.classfulCounts(ip("10.61.219.43"), mask).result();

    assertThat(mask.prefixLength()).isEqualTo(12);
    assertThat(counts.ipClass()).isEqualTo(IpClass.A);
    assertThat(counts.borrowedBits()).isEqualTo(4);
    assertThat(counts.subnetCount()).isEqualTo(16);
    assertThat(counts.hostsPerSubnet()).isEqualTo(1_048_574);
  }

  @Test
  void bullet1FirstThreeSubnets() {
    assertThat(SubnetCalculator.listSubnets(net("10.0.0.0/8"), SubnetMask.ofPrefix(12), 3).result())
        .containsExactly(net("10.0.0.0/12"), net("10.16.0.0/12"), net("10.32.0.0/12"));
  }

  @Test
  void bullet1TheHostIsInSubnet10_48() {
    Ipv4Network subnet =
        SubnetCalculator.subnetOf(ip("10.61.219.43"), SubnetMask.parse("255.240.0.0")).result();

    assertThat(subnet).isEqualTo(net("10.48.0.0/12"));
    assertThat(subnet.broadcast()).isEqualTo(ip("10.63.255.255"));
  }

  // 4.2 bullet 2: 192.14.2.0 needing 6 subnets.
  @Test
  void bullet2SixSubnetsBorrowThreeBits() {
    SubnetPlan plan = SubnetCalculator.bySubnetCount(ip("192.14.2.0"), 6).result();

    assertThat(plan.borrowedBits()).isEqualTo(3);
    assertThat(plan.subnetMask()).isEqualTo(SubnetMask.parse("255.255.255.224"));
    assertThat(plan.subnetMask().prefixLength()).isEqualTo(27);
    assertThat(plan.subnetCount()).isEqualTo(8);
    assertThat(plan.hostsPerSubnet()).isEqualTo(30);
  }

  @ParameterizedTest(name = "4.2 bullet 2: {0} usable {1} to {2}, broadcast {3}")
  @CsvSource({
    "192.14.2.0/27, 192.14.2.1, 192.14.2.30, 192.14.2.31",
    "192.14.2.32/27, 192.14.2.33, 192.14.2.62, 192.14.2.63",
    "192.14.2.64/27, 192.14.2.65, 192.14.2.94, 192.14.2.95"
  })
  void bullet2FirstThreeSubnetRanges(String cidr, String first, String last, String broadcast) {
    assertThat(
            SubnetCalculator.listSubnets(net("192.14.2.0/24"), SubnetMask.ofPrefix(27), 3).result())
        .contains(net(cidr));
    Ipv4Network subnet = net(cidr);

    assertThat(subnet.firstUsable()).isEqualTo(ip(first));
    assertThat(subnet.lastUsable()).isEqualTo(ip(last));
    assertThat(subnet.broadcast()).isEqualTo(ip(broadcast));
  }

  // 4.2 bullet 3: /29 -> 6 usable hosts.
  @Test
  void bullet3SlashTwentyNineHasSixUsableHosts() {
    assertThat(net("192.168.1.0/29").usableHostCount()).isEqualTo(6);
    assertThat(
            SubnetCalculator.classfulCounts(ip("192.168.1.0"), SubnetMask.ofPrefix(29))
                .result()
                .hostsPerSubnet())
        .isEqualTo(6);
    assertThat(SubnetCalculator.byHostCount(net("192.168.1.0/24"), 6).result().subnetMask())
        .isEqualTo(SubnetMask.ofPrefix(29));
  }

  // 4.2 exam Q2.
  @Test
  void examQ2HostSubnetAndBroadcast() {
    Ipv4Network subnet =
        SubnetCalculator.subnetOf(ip("50.85.45.133"), SubnetMask.parse("255.192.0.0")).result();

    assertThat(subnet).isEqualTo(net("50.64.0.0/10"));
    assertThat(subnet.broadcast()).isEqualTo(ip("50.127.255.255"));
  }

  // 4.2 exam Q3.
  @Test
  void examQ3SubnetAndHostCounts() {
    SubnetMask mask = SubnetMask.parse("255.255.240.0");
    ClassfulCounts counts = SubnetCalculator.classfulCounts(ip("100.10.70.0"), mask).result();

    assertThat(mask.prefixLength()).isEqualTo(20);
    assertThat(counts.ipClass()).isEqualTo(IpClass.A);
    assertThat(counts.defaultMask().prefixLength()).isEqualTo(8);
    assertThat(counts.subnetCount()).isEqualTo(4096);
    assertThat(counts.hostsPerSubnet()).isEqualTo(4094);
    assertThat(SubnetCalculator.subnetOf(ip("100.10.70.0"), mask).result())
        .isEqualTo(net("100.10.64.0/20"));
  }

  // 4.2 exam Q4: a mask shorter than the class C default is a supernet, and the app must say so.
  @Test
  void examQ4BroadcastOfASupernet() {
    SubnetMask mask = SubnetMask.parse("255.255.128.0");
    Explained<ClassfulCounts> counts = SubnetCalculator.classfulCounts(ip("200.90.0.0"), mask);
    Ipv4Network network = SubnetCalculator.subnetOf(ip("200.90.0.0"), mask).result();

    assertThat(mask.prefixLength()).isEqualTo(17);
    assertThat(network).isEqualTo(net("200.90.0.0/17"));
    assertThat(network.broadcast()).isEqualTo(ip("200.90.127.255"));
    assertThat(counts.result().isSupernet()).isTrue();
    assertThat(counts.steps()).extracting(ExplanationStep::key).contains("net.classful.supernet");
  }
}
