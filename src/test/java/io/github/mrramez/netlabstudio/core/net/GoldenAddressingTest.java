package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The golden vectors of docs/CURRICULUM.md section 4.1 (Lab 4 worksheet), one row per case. */
class GoldenAddressingTest {

  @ParameterizedTest(name = "4.1 {0} -> network {1}, broadcast {2}, mask {3}")
  @CsvSource({
    "112.16.254.1/8, 112.0.0.0, 112.255.255.255, 255.0.0.0",
    "192.16.254.1/24, 192.16.254.0, 192.16.254.255, 255.255.255.0",
    "172.16.254.1/16, 172.16.0.0, 172.16.255.255, 255.255.0.0"
  })
  void networkBroadcastAndMask(String input, String network, String broadcast, String mask) {
    Ipv4Network net = Ipv4Network.parse(input);

    assertThat(net.network()).hasToString(network);
    assertThat(net.broadcast()).hasToString(broadcast);
    assertThat(net.mask()).hasToString(mask);
  }

  @ParameterizedTest(name = "4.1 {0} = {1}")
  @CsvSource({
    "185.80.72.105, 10111001.01010000.01001000.01101001",
    "39.57.109.234, 00100111.00111001.01101101.11101010",
    "192.168.1.45, 11000000.10101000.00000001.00101101",
    "158.80.164.3, 10011110.01010000.10100100.00000011"
  })
  void decimalToBinaryAndBack(String decimal, String binary) {
    Ipv4Address address = Ipv4Address.parse(decimal);

    assertThat(address.toBinaryString()).isEqualTo(binary);
    assertThat(BinaryConverter.ipToBinary(address)).isEqualTo(binary);
    assertThat(BinaryConverter.ipFromBinary(binary)).isEqualTo(address);
    assertThat(BinaryConverter.ipFromBinaryExplained(binary).result()).isEqualTo(address);
    assertThat(BinaryConverter.ipToBinaryExplained(address).result()).isEqualTo(binary);
  }
}
