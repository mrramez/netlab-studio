package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VlsmPlannerTest {

  private static final long SEED = 20260924L;

  private static Ipv4Network net(String text) {
    return Ipv4Network.parse(text);
  }

  private static VlsmPlan plan(String base, Integer... hosts) {
    return VlsmPlanner.plan(net(base), Arrays.asList(hosts)).result();
  }

  private static List<String> networks(VlsmPlan plan) {
    return plan.allocations().stream().map(a -> a.network().toString()).toList();
  }

  @ParameterizedTest(name = "{0} hosts -> /{1}")
  @CsvSource({
    "1, 30", "2, 30", "3, 29", "6, 29", "7, 28", "14, 28", "15, 27", "30, 27", "31, 26", "62, 26",
    "63, 25", "126, 25", "127, 24", "254, 24", "255, 23", "510, 23", "511, 22"
  })
  void eachRequirementGetsTheSmallestBlockWithTwoToTheHMinusTwoHosts(int hosts, int prefix) {
    VlsmAllocation allocation = plan("10.0.0.0/16", hosts).allocations().get(0);

    assertThat(allocation.network().prefixLength()).isEqualTo(prefix);
    assertThat(allocation.usableHosts()).isGreaterThanOrEqualTo(hosts);
    assertThat(allocation.spareHosts()).isEqualTo(allocation.usableHosts() - hosts);
  }

  @Test
  void allocatesLargestFirstFromTheStartOfTheBase() {
    VlsmPlan plan = plan("192.168.1.0/24", 44, 24, 12, 2);

    assertThat(networks(plan))
        .containsExactly(
            "192.168.1.0/26", "192.168.1.64/27", "192.168.1.96/28", "192.168.1.112/30");
    assertThat(plan.nextFree()).contains(Ipv4Address.parse("192.168.1.116"));
    assertThat(plan.freeAddresses()).isEqualTo(140);
  }

  @Test
  void sortsUnsortedRequirementsButRemembersTheirOriginalPosition() {
    VlsmPlan plan = plan("192.168.1.0/24", 2, 44, 12, 24);

    assertThat(networks(plan))
        .containsExactly(
            "192.168.1.0/26", "192.168.1.64/27", "192.168.1.96/28", "192.168.1.112/30");
    assertThat(plan.allocations())
        .extracting(VlsmAllocation::requestIndex)
        .containsExactly(1, 3, 2, 0);
    assertThat(plan.allocations())
        .extracting(VlsmAllocation::requestedHosts)
        .containsExactly(44, 24, 12, 2);
  }

  @Test
  void equalRequirementsKeepTheirInputOrder() {
    VlsmPlan plan = plan("10.0.0.0/24", 10, 10, 10);

    assertThat(plan.allocations())
        .extracting(VlsmAllocation::requestIndex)
        .containsExactly(0, 1, 2);
    assertThat(networks(plan)).containsExactly("10.0.0.0/28", "10.0.0.16/28", "10.0.0.32/28");
  }

  @Test
  void aPlanThatFillsTheBaseExactlyHasNoFreeSpace() {
    VlsmPlan twoHalves = plan("10.0.0.0/24", 126, 126);
    VlsmPlan fullSlash29 = plan("10.0.0.0/29", 2, 2);
    VlsmPlan wholeBase = plan("10.0.0.0/30", 2);

    for (VlsmPlan plan : List.of(twoHalves, fullSlash29, wholeBase)) {
      assertThat(plan.nextFree()).isEmpty();
      assertThat(plan.freeAddresses()).isZero();
    }
    assertThat(networks(twoHalves)).containsExactly("10.0.0.0/25", "10.0.0.128/25");
  }

  @Test
  void aPlanCanFillTheVeryEndOfTheAddressSpace() {
    VlsmPlan plan = plan("255.255.255.0/24", 126, 126);

    assertThat(networks(plan)).containsExactly("255.255.255.0/25", "255.255.255.128/25");
    assertThat(plan.nextFree()).isEmpty();
  }

  @Test
  void aPlanCanStartAtTheStartOfTheAddressSpace() {
    VlsmPlan plan = plan("0.0.0.0/0", 1_000_000);

    assertThat(networks(plan)).containsExactly("0.0.0.0/12");
    assertThat(plan.nextFree()).contains(Ipv4Address.parse("0.16.0.0"));
  }

  @Test
  void theFirstNetworkAddressIsTheBaseNetworkAddress() {
    assertThat(plan("172.16.0.0/16", 30).allocations().get(0).network().network())
        .isEqualTo(Ipv4Address.parse("172.16.0.0"));
  }

  @Test
  void explainsEachStep() {
    Explained<VlsmPlan> explained = VlsmPlanner.plan(net("192.168.1.0/24"), List.of(12, 44, 2, 24));

    List<String> keys = explained.steps().stream().map(ExplanationStep::key).toList();
    assertThat(keys)
        .containsExactly(
            "net.vlsm.start",
            "net.vlsm.sort",
            "net.vlsm.block",
            "net.vlsm.allocate",
            "net.vlsm.block",
            "net.vlsm.allocate",
            "net.vlsm.block",
            "net.vlsm.allocate",
            "net.vlsm.block",
            "net.vlsm.allocate",
            "net.vlsm.nextFree");
    assertThat(explained.steps().get(0).args()).containsExactly("192.168.1.0/24", 4);
    assertThat(explained.steps().get(1).args()).containsExactly("12, 44, 2, 24", "44, 24, 12, 2");
    // Position in the sorted list, hosts, host bits, addresses in the block, prefix, mask.
    assertThat(explained.steps().get(2).args())
        .containsExactly(1, 44, 6, 64L, 26, "255.255.255.192");
    assertThat(explained.steps().get(3).args())
        .containsExactly("192.168.1.0/26", "192.168.1.1", "192.168.1.62", "192.168.1.63");
    assertThat(explained.steps().get(10).args()).containsExactly("192.168.1.116", 140L);
  }

  @Test
  void aFullPlanSaysThereIsNoFreeSpace() {
    Explained<VlsmPlan> explained = VlsmPlanner.plan(net("10.0.0.0/30"), List.of(2));

    assertThat(explained.steps().get(explained.steps().size() - 1).key())
        .isEqualTo("net.vlsm.noFreeSpace");
  }

  @Test
  void allocationsCannotBeChanged() {
    VlsmPlan plan = plan("10.0.0.0/24", 10);

    assertThatThrownBy(() -> plan.allocations().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // ---------------------------------------------------------------- does not fit

  @Test
  void reportsTheRequirementThatDoesNotFit() {
    assertThatThrownBy(() -> plan("192.168.1.0/24", 200, 100))
        .isInstanceOfSatisfying(
            VlsmDoesNotFitException.class,
            e -> {
              assertThat(e.requestIndex()).isEqualTo(1);
              assertThat(e.requestedHosts()).isEqualTo(100);
              assertThat(e.neededAddresses()).isEqualTo(128);
              assertThat(e.availableAddresses()).isZero();
              assertThat(e.getMessage())
                  .contains("#2")
                  .contains("100 hosts")
                  .contains("128 addresses")
                  .contains("192.168.1.0/24");
            });
  }

  @Test
  void aRequirementLargerThanTheWholeBaseDoesNotFit() {
    assertThatThrownBy(() -> plan("192.168.1.0/24", 300))
        .isInstanceOfSatisfying(
            VlsmDoesNotFitException.class,
            e -> {
              assertThat(e.neededAddresses()).isEqualTo(512);
              assertThat(e.availableAddresses()).isEqualTo(256);
            });
  }

  @Test
  void theFailingRequirementIsTheFirstOneInSortedOrder() {
    // 60 + 60 + 20: the third block (32) no longer fits after two /26 in a /25.
    assertThatThrownBy(() -> plan("10.0.0.0/25", 20, 60, 60))
        .isInstanceOfSatisfying(
            VlsmDoesNotFitException.class,
            e -> {
              assertThat(e.requestIndex()).isZero();
              assertThat(e.requestedHosts()).isEqualTo(20);
              assertThat(e.neededAddresses()).isEqualTo(32);
              assertThat(e.availableAddresses()).isZero();
            });
  }

  @ParameterizedTest
  @ValueSource(ints = {1_000_000_000, Integer.MAX_VALUE})
  void hugeRequirementsFailCleanlyWithoutOverflow(int hosts) {
    assertThatThrownBy(() -> plan("10.0.0.0/8", hosts)).isInstanceOf(VlsmDoesNotFitException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"10.0.0.0/31", "10.0.0.1/32"})
  void aBaseTooSmallForAnySubnetDoesNotFit(String base) {
    assertThatThrownBy(() -> plan(base, 1)).isInstanceOf(VlsmDoesNotFitException.class);
  }

  // ---------------------------------------------------------------- invalid input

  @Test
  void needsAtLeastOneRequirement() {
    assertThatThrownBy(() -> VlsmPlanner.plan(net("10.0.0.0/24"), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
  void everyRequirementNeedsAtLeastOneHost(int hosts) {
    assertThatThrownBy(() -> plan("10.0.0.0/24", 10, hosts))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("#2")
        .hasMessageContaining("at least 1");
  }

  @Test
  void capsTheNumberOfRequirements() {
    List<Integer> many = new ArrayList<>();
    for (int i = 0; i < 1025; i++) {
      many.add(1);
    }

    assertThatThrownBy(() -> VlsmPlanner.plan(net("10.0.0.0/8"), many))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1024");
    assertThat(VlsmPlanner.plan(net("10.0.0.0/8"), many.subList(0, 1024)).result().allocations())
        .hasSize(1024);
  }

  @Test
  void rejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> VlsmPlanner.plan(null, List.of(1)));
    assertThatNullPointerException().isThrownBy(() -> VlsmPlanner.plan(net("10.0.0.0/24"), null));
    assertThatNullPointerException()
        .isThrownBy(() -> VlsmPlanner.plan(net("10.0.0.0/24"), Arrays.asList(1, null)));
  }

  @Test
  void theListOfRequirementsIsNotRetainedOrChanged() {
    List<Integer> hosts = new ArrayList<>(List.of(2, 44, 12, 24));

    VlsmPlanner.plan(net("192.168.1.0/24"), hosts);

    assertThat(hosts).containsExactly(2, 44, 12, 24);
  }

  // ---------------------------------------------------------------- random inputs

  @Test
  void invariantsHoldForRandomRequirements() {
    Random random = new Random(SEED);
    int successes = 0;
    int failures = 0;
    for (int i = 0; i < 5_000; i++) {
      int basePrefix = 8 + random.nextInt(23);
      Ipv4Network base =
          Ipv4Network.containing(
              new Ipv4Address(random.nextInt()), SubnetMask.ofPrefix(basePrefix));
      int count = 1 + random.nextInt(8);
      List<Integer> hosts = new ArrayList<>();
      long totalBlocks = 0;
      for (int k = 0; k < count; k++) {
        int h = 1 + random.nextInt((int) Math.min(base.totalAddresses() / 3 + 1, 100_000));
        hosts.add(h);
        // Smallest power of two that is at least h + 2 (and at least 4).
        totalBlocks += Long.highestOneBit(h + 1L) << 1;
      }
      String context = "seed " + SEED + ", iteration " + i + ", " + base + " " + hosts;

      boolean shouldFit = totalBlocks <= base.totalAddresses();
      if (!shouldFit) {
        // Blocks that are powers of two, placed largest first, leave no gaps: it fits exactly
        // when the blocks add up to no more than the base.
        assertThatThrownBy(() -> VlsmPlanner.plan(base, hosts))
            .as(context)
            .isInstanceOf(VlsmDoesNotFitException.class);
        failures++;
        continue;
      }
      successes++;
      VlsmPlan plan = VlsmPlanner.plan(base, hosts).result();

      assertThat(plan.allocations()).as(context).hasSize(count);
      long used = 0;
      Ipv4Network previous = null;
      for (VlsmAllocation allocation : plan.allocations()) {
        Ipv4Network network = allocation.network();
        assertThat(base.contains(network)).as(context).isTrue();
        assertThat(allocation.usableHosts())
            .as(context)
            .isGreaterThanOrEqualTo(allocation.requestedHosts());
        // One bit smaller would not have held the requirement (the smallest block is a /30).
        if (network.prefixLength() < 30) {
          assertThat(network.totalAddresses() / 2 - 2)
              .as(context)
              .isLessThan(allocation.requestedHosts());
        }
        assertThat(hosts.get(allocation.requestIndex()))
            .as(context)
            .isEqualTo(allocation.requestedHosts());
        if (previous != null) {
          assertThat(network.overlaps(previous)).as(context).isFalse();
          assertThat(network.network().toUnsignedLong())
              .as(context)
              .isEqualTo(previous.broadcast().toUnsignedLong() + 1);
          assertThat(network.totalAddresses())
              .as(context)
              .isLessThanOrEqualTo(previous.totalAddresses());
        }
        used += network.totalAddresses();
        previous = network;
      }
      assertThat(plan.allocations().stream().map(VlsmAllocation::requestIndex).distinct().count())
          .as(context)
          .isEqualTo(count);
      assertThat(plan.freeAddresses()).as(context).isEqualTo(base.totalAddresses() - used);
      if (plan.freeAddresses() > 0) {
        assertThat(plan.nextFree()).as(context).isPresent();
        assertThat(base.contains(plan.nextFree().orElseThrow())).as(context).isTrue();
      } else {
        assertThat(plan.nextFree()).as(context).isEmpty();
      }
    }
    // The generator must exercise both outcomes, or the invariants above prove little.
    assertThat(successes).isGreaterThan(500);
    assertThat(failures).isGreaterThan(500);
  }
}
