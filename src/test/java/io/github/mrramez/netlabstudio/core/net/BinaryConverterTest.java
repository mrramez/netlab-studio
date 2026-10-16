package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BinaryConverterTest {

  @ParameterizedTest
  @CsvSource({
    "0, 8, 00000000",
    "5, 8, 00000101",
    "185, 8, 10111001",
    "255, 8, 11111111",
    "1, 1, 1",
    "4095, 12, 111111111111",
    "4294967295, 32, 11111111111111111111111111111111"
  })
  void convertsToBinaryWithFixedWidth(long value, int width, String expected) {
    assertThat(BinaryConverter.toBinary(value, width)).isEqualTo(expected);
    assertThat(BinaryConverter.fromBinary(expected)).isEqualTo(value);
  }

  @ParameterizedTest
  @CsvSource({"-1, 8", "256, 8", "2, 1", "0, 0", "0, 64", "1, -3"})
  void rejectsAValueThatDoesNotFitOrABadWidth(long value, int width) {
    assertThatThrownBy(() -> BinaryConverter.toBinary(value, width))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tooBigValueMessageSaysHowManyBitsAreNeeded() {
    assertThatThrownBy(() -> BinaryConverter.toBinary(256, 8))
        .hasMessageContaining("256")
        .hasMessageContaining("8 bits");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "102", "1 0", "1_0", "0b101", "-1", "١٠"})
  void fromBinaryRejectsAnythingButZerosAndOnes(String text) {
    assertThatThrownBy(() -> BinaryConverter.fromBinary(text))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromBinaryRejectsTextLongerThanSixtyThreeBits() {
    assertThatThrownBy(() -> BinaryConverter.fromBinary("1".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("63");
    assertThat(BinaryConverter.fromBinary("1".repeat(63))).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> BinaryConverter.fromBinary(null));
    assertThatNullPointerException().isThrownBy(() -> BinaryConverter.ipFromBinary(null));
    assertThatNullPointerException().isThrownBy(() -> BinaryConverter.ipFromBinaryExplained(null));
    assertThatNullPointerException().isThrownBy(() -> BinaryConverter.ipToBinary(null));
    assertThatNullPointerException().isThrownBy(() -> BinaryConverter.ipToBinaryExplained(null));
  }

  @Test
  void octetToBinaryHasEightDigits() {
    assertThat(BinaryConverter.octetToBinary(185)).isEqualTo("10111001");
    assertThat(BinaryConverter.octetToBinary(0)).isEqualTo("00000000");
    assertThatThrownBy(() -> BinaryConverter.octetToBinary(256))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BinaryConverter.octetToBinary(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void convertsAnAddressBothWays() {
    Ipv4Address address = Ipv4Address.parse("185.80.72.105");

    assertThat(BinaryConverter.ipToBinary(address))
        .isEqualTo("10111001.01010000.01001000.01101001");
    assertThat(BinaryConverter.ipFromBinary("10111001.01010000.01001000.01101001"))
        .isEqualTo(address);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "10111001.01010000.01001000",
        "10111001.01010000.01001000.01101001.00000000",
        "10111001.01010000.01001000.0110100",
        "10111001.01010000.01001000.011010011",
        "10111001.01010000.01001000.0110100a",
        "10111001010100000100100001101001",
        "10111001..01001000.01101001",
        ""
      })
  void ipFromBinaryRejectsMalformedText(String text) {
    assertThatThrownBy(() -> BinaryConverter.ipFromBinary(text))
        .isInstanceOf(InvalidAddressException.class);
  }

  @Test
  void placeValueStepsShowTheGreedyDecomposition() {
    Explained<String> explained = BinaryConverter.octetToBinaryExplained(185);

    assertThat(explained.result()).isEqualTo("10111001");
    List<ExplanationStep> steps = explained.steps();
    assertThat(steps).hasSize(10);
    assertThat(steps.get(0)).isEqualTo(ExplanationStep.of("net.binary.start", 185));
    // Place value, does it fit, what is left afterwards.
    assertThat(steps.get(1)).isEqualTo(ExplanationStep.of("net.binary.placeValue", 128, true, 57));
    assertThat(steps.get(2)).isEqualTo(ExplanationStep.of("net.binary.placeValue", 64, false, 57));
    assertThat(steps.get(3)).isEqualTo(ExplanationStep.of("net.binary.placeValue", 32, true, 25));
    assertThat(steps.get(4)).isEqualTo(ExplanationStep.of("net.binary.placeValue", 16, true, 9));
    assertThat(steps.get(5)).isEqualTo(ExplanationStep.of("net.binary.placeValue", 8, true, 1));
    assertThat(steps.get(6)).isEqualTo(ExplanationStep.of("net.binary.placeValue", 4, false, 1));
    assertThat(steps.get(7)).isEqualTo(ExplanationStep.of("net.binary.placeValue", 2, false, 1));
    assertThat(steps.get(8)).isEqualTo(ExplanationStep.of("net.binary.placeValue", 1, true, 0));
    assertThat(steps.get(9))
        .isEqualTo(ExplanationStep.of("net.binary.octetResult", 185, "10111001"));
  }

  @Test
  void addressToBinaryExplainsEveryOctet() {
    Explained<String> explained =
        BinaryConverter.ipToBinaryExplained(Ipv4Address.parse("192.168.1.45"));

    assertThat(explained.result()).isEqualTo("11000000.10101000.00000001.00101101");
    // Per octet: a header, 8 place values and a result; then one summary line.
    assertThat(explained.steps()).hasSize(4 * 10 + 1);
    assertThat(explained.steps().get(0)).isEqualTo(ExplanationStep.of("net.binary.octet", 1, 192));
    assertThat(explained.steps().get(10)).isEqualTo(ExplanationStep.of("net.binary.octet", 2, 168));
    assertThat(explained.steps().get(explained.steps().size() - 1))
        .isEqualTo(
            ExplanationStep.of(
                "net.binary.addressResult", "192.168.1.45", "11000000.10101000.00000001.00101101"));
  }

  @Test
  void binaryToAddressExplainsEveryGroupAsASum() {
    Explained<Ipv4Address> explained =
        BinaryConverter.ipFromBinaryExplained("10111001.01010000.01001000.01101001");

    assertThat(explained.result()).hasToString("185.80.72.105");
    assertThat(explained.steps()).hasSize(5);
    assertThat(explained.steps().get(0))
        .isEqualTo(ExplanationStep.of("net.binary.toDecimal", 1, "10111001", "128+32+16+8+1", 185));
    assertThat(explained.steps().get(1))
        .isEqualTo(ExplanationStep.of("net.binary.toDecimal", 2, "01010000", "64+16", 80));
    assertThat(explained.steps().get(2))
        .isEqualTo(ExplanationStep.of("net.binary.toDecimal", 3, "01001000", "64+8", 72));
    assertThat(explained.steps().get(3))
        .isEqualTo(ExplanationStep.of("net.binary.toDecimal", 4, "01101001", "64+32+8+1", 105));
    assertThat(explained.steps().get(4))
        .isEqualTo(
            ExplanationStep.of(
                "net.binary.addressFromBinary",
                "10111001.01010000.01001000.01101001",
                "185.80.72.105"));
  }

  @Test
  void aGroupOfZerosSumsToZero() {
    Explained<Ipv4Address> explained =
        BinaryConverter.ipFromBinaryExplained("00000000.00000000.00000000.00000000");

    assertThat(explained.steps().get(0).args()).containsExactly(1, "00000000", "0", 0);
  }

  @Test
  void anInvalidBinaryAddressFailsInTheExplainedFormToo() {
    assertThatThrownBy(() -> BinaryConverter.ipFromBinaryExplained("101"))
        .isInstanceOf(InvalidAddressException.class);
  }
}
