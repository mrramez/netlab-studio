package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MacGeneratorTest {

  private static List<MacAddress> take(MacGenerator generator, int count) {
    List<MacAddress> macs = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      macs.add(generator.next());
    }
    return macs;
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, 1L, 42L, -1L, Long.MIN_VALUE, Long.MAX_VALUE})
  void theSameSeedGivesTheSameSequence(long seed) {
    assertThat(take(new MacGenerator(seed), 200)).isEqualTo(take(new MacGenerator(seed), 200));
  }

  @Test
  void differentSeedsGiveDifferentSequences() {
    assertThat(take(new MacGenerator(1), 20)).isNotEqualTo(take(new MacGenerator(2), 20));
  }

  // These outputs of SplitMix64 for seed 0 are the published reference values, so the generator
  // keeps producing the same MACs on every JDK and in every future release.
  @Test
  void rawOutputMatchesTheSplitMix64ReferenceVectors() {
    MacGenerator generator = new MacGenerator(0);

    assertThat(generator.nextRaw()).isEqualTo(0xE220A8397B1DCDAFL);
    assertThat(generator.nextRaw()).isEqualTo(0x6E789E6AA1B965F4L);
    assertThat(generator.nextRaw()).isEqualTo(0x06C45D188009454FL);
  }

  @Test
  void firstAddressesForSeedZeroAreFixed() {
    assertThat(take(new MacGenerator(0), 3))
        .extracting(MacAddress::toString)
        .containsExactly("aa:39:7b:1d:cd:af", "9e:6a:a1:b9:65:f4", "5e:18:80:09:45:4f");
  }

  @Test
  void everyAddressIsAUniqueLocallyAdministeredUnicastAddress() {
    List<MacAddress> macs = take(new MacGenerator(20260924L), 20_000);
    Set<MacAddress> distinct = new HashSet<>(macs);

    assertThat(distinct).hasSameSizeAs(macs);
    assertThat(macs)
        .allSatisfy(
            mac -> {
              assertThat(mac.isLocallyAdministered()).as(mac.toString()).isTrue();
              assertThat(mac.isUnicast()).as(mac.toString()).isTrue();
              assertThat(mac.isBroadcast()).as(mac.toString()).isFalse();
            });
  }

  @Test
  void skipsARawValueThatWouldRepeatAnAddress() {
    long[] raw = {0x0000_1111_2222_3333L, 0x0000_1111_2222_3333L, 0x0000_4444_5555_6666L};
    int[] index = {0};
    MacGenerator generator = new MacGenerator(() -> raw[Math.min(index[0]++, raw.length - 1)]);

    MacAddress first = generator.next();
    MacAddress second = generator.next();

    assertThat(first).hasToString("12:11:22:22:33:33");
    assertThat(second).hasToString("46:44:55:55:66:66");
  }

  @Test
  void givesUpInsteadOfLoopingForeverWhenTheSourceIsStuck() {
    MacGenerator generator = new MacGenerator(() -> 0x0000_1111_2222_3333L);
    generator.next();

    assertThatThrownBy(generator::next)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no new MAC address");
  }
}
