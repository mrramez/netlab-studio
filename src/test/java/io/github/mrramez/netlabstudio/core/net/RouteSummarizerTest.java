package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RouteSummarizerTest {

  private static final long SEED = 20260924L;

  private static Ipv4Network net(String text) {
    return Ipv4Network.parse(text);
  }

  private static List<Ipv4Network> nets(String... texts) {
    return Arrays.stream(texts).map(RouteSummarizerTest::net).toList();
  }

  private static Explained<RouteSummary> summarize(String... texts) {
    return RouteSummarizer.summarize(nets(texts));
  }

  private static List<String> keys(Explained<?> explained) {
    return explained.steps().stream().map(ExplanationStep::key).toList();
  }

  @Test
  void fourContiguousNetworksSummariseExactly() {
    Explained<RouteSummary> explained =
        summarize("192.168.0.0/24", "192.168.1.0/24", "192.168.2.0/24", "192.168.3.0/24");
    RouteSummary summary = explained.result();

    assertThat(summary.summary()).isEqualTo(net("192.168.0.0/22"));
    assertThat(summary.commonPrefixBits()).isEqualTo(22);
    assertThat(summary.isExact()).isTrue();
    assertThat(summary.extraAddresses()).isZero();
    assertThat(summary.extraNetworks()).isEmpty();
    assertThat(keys(explained))
        .containsExactly(
            "net.summary.start",
            "net.summary.binary",
            "net.summary.binary",
            "net.summary.binary",
            "net.summary.binary",
            "net.summary.commonBits",
            "net.summary.result",
            "net.summary.exact");
    assertThat(explained.steps().get(0).args()).containsExactly(4, 4);
    assertThat(explained.steps().get(1).args())
        .containsExactly("192.168.0.0/24", "11000000.10101000.00000000.00000000");
    assertThat(explained.steps().get(5).args())
        .containsExactly(22, "11000000.10101000.00000000.00000000");
    assertThat(explained.steps().get(6).args())
        .containsExactly("192.168.0.0/22", "255.255.252.0", 22);
  }

  @Test
  void aSummaryThatAlsoCoversOtherNetworksSaysWhichOnes() {
    Explained<RouteSummary> explained = summarize("192.168.1.0/24", "192.168.2.0/24");
    RouteSummary summary = explained.result();

    assertThat(summary.summary()).isEqualTo(net("192.168.0.0/22"));
    assertThat(summary.isExact()).isFalse();
    assertThat(summary.extraAddresses()).isEqualTo(512);
    assertThat(summary.extraNetworks())
        .containsExactly(net("192.168.0.0/24"), net("192.168.3.0/24"));
    assertThat(keys(explained))
        .contains("net.summary.extra", "net.summary.extraNetwork")
        .doesNotContain("net.summary.exact");
    ExplanationStep extra =
        explained.steps().stream()
            .filter(step -> step.key().equals("net.summary.extra"))
            .findFirst()
            .orElseThrow();
    assertThat(extra.args()).containsExactly(512L, 2);
  }

  @Test
  void extraNetworksAreReportedAsTheFewestAlignedBlocks() {
    // 10.0.0.0/24 and 10.0.7.0/24 summarise to 10.0.0.0/21; the six in between are extra.
    RouteSummary summary = summarize("10.0.0.0/24", "10.0.7.0/24").result();

    assertThat(summary.summary()).isEqualTo(net("10.0.0.0/21"));
    assertThat(summary.extraNetworks())
        .containsExactly(
            net("10.0.1.0/24"), net("10.0.2.0/23"), net("10.0.4.0/23"), net("10.0.6.0/24"));
    assertThat(summary.extraAddresses()).isEqualTo(6 * 256);
  }

  @Test
  void aSingleNetworkSummarisesToItself() {
    Explained<RouteSummary> explained = summarize("172.16.5.0/24");

    assertThat(explained.result().summary()).isEqualTo(net("172.16.5.0/24"));
    assertThat(explained.result().commonPrefixBits()).isEqualTo(24);
    assertThat(explained.result().isExact()).isTrue();
  }

  @Test
  void duplicatesCountOnce() {
    Explained<RouteSummary> explained = summarize("10.1.0.0/16", "10.1.0.0/16", "10.1.0.0/16");

    assertThat(explained.result().summary()).isEqualTo(net("10.1.0.0/16"));
    assertThat(explained.result().isExact()).isTrue();
    assertThat(explained.steps().get(0).args()).containsExactly(3, 1);
    assertThat(keys(explained).stream().filter(k -> k.equals("net.summary.binary"))).hasSize(1);
  }

  @Test
  void overlappingInputsAreLimitedByTheShortestPrefix() {
    Explained<RouteSummary> explained = summarize("10.0.0.0/8", "10.1.0.0/16");

    assertThat(explained.result().summary()).isEqualTo(net("10.0.0.0/8"));
    assertThat(explained.result().isExact()).isTrue();
    assertThat(keys(explained)).contains("net.summary.limitedByPrefix");
  }

  @Test
  void mixedPrefixLengthsAreSummarisedByTheirCommonBits() {
    RouteSummary summary = summarize("192.168.0.0/23", "192.168.2.0/24").result();

    assertThat(summary.summary()).isEqualTo(net("192.168.0.0/22"));
    assertThat(summary.extraNetworks()).containsExactly(net("192.168.3.0/24"));
    assertThat(summary.extraAddresses()).isEqualTo(256);
  }

  @Test
  void networksThatShareNothingSummariseToTheDefaultRoute() {
    RouteSummary summary = summarize("10.0.0.0/8", "192.168.0.0/16").result();

    assertThat(summary.summary()).isEqualTo(net("0.0.0.0/0"));
    assertThat(summary.commonPrefixBits()).isZero();
    assertThat(summary.isExact()).isFalse();
    assertThat(summary.extraAddresses()).isEqualTo((1L << 32) - (1L << 24) - (1L << 16));
  }

  @Test
  void theOrderOfTheInputDoesNotMatter() {
    List<Ipv4Network> networks =
        new ArrayList<>(nets("172.16.0.0/16", "172.19.0.0/16", "172.17.0.0/16"));
    RouteSummary expected = RouteSummarizer.summarize(networks).result();

    Collections.reverse(networks);

    assertThat(RouteSummarizer.summarize(networks).result()).isEqualTo(expected);
    assertThat(expected.summary()).isEqualTo(net("172.16.0.0/14"));
  }

  @Test
  void rejectsBadLists() {
    assertThatThrownBy(() -> RouteSummarizer.summarize(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one");
    List<Ipv4Network> many = Collections.nCopies(1025, net("10.0.0.0/8"));
    assertThatThrownBy(() -> RouteSummarizer.summarize(many))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1024");
    assertThat(RouteSummarizer.summarize(many.subList(0, 1024)).result().isExact()).isTrue();
  }

  @Test
  void rejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> RouteSummarizer.summarize(null));
    assertThatNullPointerException()
        .isThrownBy(() -> RouteSummarizer.summarize(Arrays.asList(net("10.0.0.0/8"), null)));
  }

  @Test
  void summariesRejectInconsistentValues() {
    assertThatNullPointerException().isThrownBy(() -> new RouteSummary(null, List.of(), 0));
    assertThatThrownBy(() -> new RouteSummary(net("10.0.0.0/8"), List.of(), -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void summariesAreMinimalAndAccountForEveryExtraAddress() {
    Random random = new Random(SEED);
    for (int i = 0; i < 2_000; i++) {
      Ipv4Network space =
          Ipv4Network.containing(new Ipv4Address(random.nextInt()), SubnetMask.ofPrefix(16));
      int count = 1 + random.nextInt(6);
      List<Ipv4Network> inputs = new ArrayList<>();
      boolean[] covered = new boolean[256];
      for (int k = 0; k < count; k++) {
        int prefix = 20 + random.nextInt(5);
        Ipv4Network input =
            Ipv4Network.containing(
                space.network().plus(random.nextInt(65_536)), SubnetMask.ofPrefix(prefix));
        inputs.add(input);
        for (long a = input.network().toUnsignedLong();
            a <= input.broadcast().toUnsignedLong();
            a += 256) {
          covered[(int) ((a - space.network().toUnsignedLong()) / 256)] = true;
        }
      }
      String context = "seed " + SEED + ", iteration " + i + ", " + inputs;

      RouteSummary result = RouteSummarizer.summarize(inputs).result();
      Ipv4Network summary = result.summary();

      for (Ipv4Network input : inputs) {
        assertThat(summary.contains(input)).as(context).isTrue();
      }
      // One bit longer would no longer contain every input.
      if (summary.prefixLength() < 32) {
        Ipv4Network narrower =
            Ipv4Network.containing(
                inputs.get(0).network(), SubnetMask.ofPrefix(summary.prefixLength() + 1));
        assertThat(inputs).as(context).anyMatch(input -> !narrower.contains(input));
      }
      long coveredAddresses = 0;
      for (boolean c : covered) {
        coveredAddresses += c ? 256 : 0;
      }
      assertThat(result.extraAddresses())
          .as(context)
          .isEqualTo(summary.totalAddresses() - coveredAddresses);
      long listed = 0;
      for (Ipv4Network extra : result.extraNetworks()) {
        assertThat(summary.contains(extra)).as(context).isTrue();
        for (Ipv4Network input : inputs) {
          assertThat(extra.overlaps(input)).as(context).isFalse();
        }
        listed += extra.totalAddresses();
      }
      assertThat(listed).as(context).isEqualTo(result.extraAddresses());
      assertThat(result.isExact()).as(context).isEqualTo(result.extraAddresses() == 0);
    }
  }
}
