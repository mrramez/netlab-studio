package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The golden vectors of docs/CURRICULUM.md section 4.3 (VLSM worksheet and exam Q1). */
class GoldenVlsmTest {

  private static Ipv4Address ip(String text) {
    return Ipv4Address.parse(text);
  }

  private static Ipv4Network net(String text) {
    return Ipv4Network.parse(text);
  }

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of(
            "192.168.1.0/24",
            List.of(44, 24, 12, 2),
            List.of(
                "192.168.1.0/26 192.168.1.63",
                "192.168.1.64/27 192.168.1.95",
                "192.168.1.96/28 192.168.1.111",
                "192.168.1.112/30 192.168.1.115"),
            "192.168.1.116"),
        Arguments.of(
            "172.16.0.0/16",
            List.of(340, 250, 31, 20, 8),
            // 31 hosts need 33 addresses, so the block is 64 and the mask /26.
            List.of(
                "172.16.0.0/23 172.16.1.255",
                "172.16.2.0/24 172.16.2.255",
                "172.16.3.0/26 172.16.3.63",
                "172.16.3.64/27 172.16.3.95",
                "172.16.3.96/28 172.16.3.111"),
            "172.16.3.112"));
  }

  @ParameterizedTest(name = "4.3 {0} needing {1}")
  @MethodSource("cases")
  void worksheetAllocations(
      String base, List<Integer> hosts, List<String> expected, String nextFree) {
    VlsmPlan plan = VlsmPlanner.plan(net(base), hosts).result();

    assertThat(plan.allocations())
        .extracting(a -> a.network() + " " + a.network().broadcast())
        .containsExactlyElementsOf(expected);
    assertThat(plan.nextFree()).contains(ip(nextFree));
  }

  // Exam Q1: three LANs of at least 45 hosts and one router-to-router link in 10.132.10.0/24.
  @Test
  void examQ1AddressingPlan() {
    VlsmPlan plan = VlsmPlanner.plan(net("10.132.10.0/24"), List.of(45, 45, 45, 2)).result();

    assertThat(plan.allocations())
        .extracting(a -> a.network().toString())
        .containsExactly(
            "10.132.10.0/26", "10.132.10.64/26", "10.132.10.128/26", "10.132.10.192/30");
    List<VlsmAllocation> lans = plan.allocations().subList(0, 3);
    assertThat(lans)
        .allSatisfy(
            lan -> {
              assertThat(lan.network().mask()).hasToString("255.255.255.192");
              assertThat(lan.usableHosts()).isEqualTo(62);
            });

    // Convention from the exam: the router takes the first usable address of each network.
    Ipv4Network wan = plan.allocations().get(3).network();
    assertThat(wan.firstUsable()).isEqualTo(ip("10.132.10.193"));
    assertThat(wan.lastUsable()).isEqualTo(ip("10.132.10.194"));
    assertThat(lans.get(0).network().firstUsable()).isEqualTo(ip("10.132.10.1"));
    assertThat(lans.get(1).network().firstUsable()).isEqualTo(ip("10.132.10.65"));
    assertThat(lans.get(2).network().firstUsable()).isEqualTo(ip("10.132.10.129"));
  }
}
