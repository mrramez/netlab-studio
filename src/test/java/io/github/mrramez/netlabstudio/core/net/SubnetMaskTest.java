package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SubnetMaskTest {

  @ParameterizedTest
  @CsvSource({
    "0, 0.0.0.0, 255.255.255.255",
    "8, 255.0.0.0, 0.255.255.255",
    "12, 255.240.0.0, 0.15.255.255",
    "16, 255.255.0.0, 0.0.255.255",
    "17, 255.255.128.0, 0.0.127.255",
    "20, 255.255.240.0, 0.0.15.255",
    "24, 255.255.255.0, 0.0.0.255",
    "26, 255.255.255.192, 0.0.0.63",
    "27, 255.255.255.224, 0.0.0.31",
    "29, 255.255.255.248, 0.0.0.7",
    "30, 255.255.255.252, 0.0.0.3",
    "31, 255.255.255.254, 0.0.0.1",
    "32, 255.255.255.255, 0.0.0.0"
  })
  void prefixAndDottedFormsAgree(int prefix, String dotted, String wildcard) {
    SubnetMask mask = SubnetMask.ofPrefix(prefix);

    assertThat(mask.prefixLength()).isEqualTo(prefix);
    assertThat(mask).hasToString(dotted);
    assertThat(mask.toAddress()).hasToString(dotted);
    assertThat(mask.wildcard()).hasToString(wildcard);
    assertThat(mask.hostBits()).isEqualTo(32 - prefix);
    assertThat(mask.toCidrString()).isEqualTo("/" + prefix);
    assertThat(SubnetMask.parse(dotted)).isEqualTo(mask);
    assertThat(SubnetMask.parse("/" + prefix)).isEqualTo(mask);
    assertThat(SubnetMask.fromDotted(dotted)).isEqualTo(mask);
  }

  @Test
  void toIntIsTheBitPattern() {
    assertThat(SubnetMask.ofPrefix(0).toInt()).isZero();
    assertThat(SubnetMask.ofPrefix(24).toInt()).isEqualTo(0xFFFFFF00);
    assertThat(SubnetMask.ofPrefix(32).toInt()).isEqualTo(-1);
  }

  @Test
  void aPrefixWithALeadingZeroIsStillAPrefix() {
    assertThat(SubnetMask.parse("/08")).isEqualTo(SubnetMask.ofPrefix(8));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 33, 100, Integer.MIN_VALUE, Integer.MAX_VALUE})
  void rejectsAPrefixOutsideZeroToThirtyTwo(int prefix) {
    assertThatThrownBy(() -> SubnetMask.ofPrefix(prefix))
        .isInstanceOf(InvalidAddressException.class)
        .hasMessageContaining("between 0 and 32");
    assertThatThrownBy(() -> new SubnetMask(prefix)).isInstanceOf(InvalidAddressException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/33", "/99", "/-1", "/", "/ 24", "/2 4", "/abc", "/024", "/٢٤"})
  void rejectsABadPrefixText(String text) {
    assertThatThrownBy(() -> SubnetMask.parse(text)).isInstanceOf(InvalidAddressException.class);
  }

  @Test
  void aPrefixTextOutOfRangeSaysSo() {
    assertThatThrownBy(() -> SubnetMask.parse("/33"))
        .hasMessageContaining("'/33'")
        .hasMessageContaining("between 0 and 32");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "255.0.255.0",
        "255.255.255.1",
        "0.255.0.0",
        "255.255.0.255",
        "1.1.1.1",
        "255.255.255.253",
        "128.0.0.1"
      })
  void rejectsMasksWhoseOnesAreNotContiguous(String dotted) {
    assertThatThrownBy(() -> SubnetMask.fromDotted(dotted))
        .isInstanceOf(InvalidAddressException.class)
        .hasMessageContaining(dotted)
        .hasMessageContaining("1s followed by")
        .hasMessageContaining(Ipv4Address.parse(dotted).toBinaryString());
    assertThatThrownBy(() -> SubnetMask.parse(dotted)).isInstanceOf(InvalidAddressException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "24", "255.255.255", "255.255.256.0", "abc", "255.255.255.0/24"})
  void rejectsOtherBadText(String text) {
    assertThatThrownBy(() -> SubnetMask.parse(text)).isInstanceOf(InvalidAddressException.class);
  }

  @Test
  void aBareNumberHintsAtTheSlash() {
    assertThatThrownBy(() -> SubnetMask.parse("24")).hasMessageContaining("/24");
  }

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> SubnetMask.parse(null));
    assertThatNullPointerException().isThrownBy(() -> SubnetMask.fromDotted(null));
    assertThatNullPointerException().isThrownBy(() -> SubnetMask.fromAddress(null));
  }

  @Test
  void validatesMaskAddressesWithoutThrowing() {
    assertThat(SubnetMask.isValidMask(Ipv4Address.parse("255.255.240.0"))).isTrue();
    assertThat(SubnetMask.isValidMask(Ipv4Address.parse("0.0.0.0"))).isTrue();
    assertThat(SubnetMask.isValidMask(Ipv4Address.parse("255.255.255.255"))).isTrue();
    assertThat(SubnetMask.isValidMask(Ipv4Address.parse("255.0.255.0"))).isFalse();
    assertThat(SubnetMask.isValidMask(Ipv4Address.parse("0.0.0.255"))).isFalse();
  }

  @Test
  void everyPrefixSurvivesARoundTrip() {
    for (int prefix = 0; prefix <= 32; prefix++) {
      SubnetMask mask = SubnetMask.ofPrefix(prefix);
      String context = "prefix " + prefix;

      assertThat(SubnetMask.fromAddress(mask.toAddress())).as(context).isEqualTo(mask);
      assertThat(mask.wildcard().value()).as(context).isEqualTo(~mask.toInt());
      assertThat(Integer.bitCount(mask.toInt())).as(context).isEqualTo(prefix);
      assertThat(mask.hostBits() + mask.prefixLength()).as(context).isEqualTo(32);
    }
  }
}
