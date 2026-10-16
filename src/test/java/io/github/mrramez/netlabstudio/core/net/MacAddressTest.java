package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class MacAddressTest {

  private static final long SEED = 20260924L;
  private static final long EXPECTED = 0x001A_2B3C_4D5EL;

  @ParameterizedTest
  @ValueSource(
      strings = {
        "00:1a:2b:3c:4d:5e",
        "00:1A:2B:3C:4D:5E",
        "00-1a-2b-3c-4d-5e",
        "00-1A-2B-3C-4D-5E",
        "001a.2b3c.4d5e",
        "001A.2B3C.4D5E"
      })
  void parsesAllThreeStylesInEitherCase(String text) {
    assertThat(MacAddress.parse(text).value()).isEqualTo(EXPECTED);
  }

  @Test
  void formatsEachStyle() {
    MacAddress mac = new MacAddress(EXPECTED);

    assertThat(mac.format(MacStyle.COLON)).isEqualTo("00:1a:2b:3c:4d:5e");
    assertThat(mac.format(MacStyle.HYPHEN)).isEqualTo("00-1a-2b-3c-4d-5e");
    assertThat(mac.format(MacStyle.CISCO_DOT)).isEqualTo("001a.2b3c.4d5e");
    assertThat(mac.format(MacStyle.COLON, true)).isEqualTo("00:1A:2B:3C:4D:5E");
    assertThat(mac.format(MacStyle.HYPHEN, true)).isEqualTo("00-1A-2B-3C-4D-5E");
    assertThat(mac.format(MacStyle.CISCO_DOT, true)).isEqualTo("001A.2B3C.4D5E");
    assertThat(mac).hasToString("00:1a:2b:3c:4d:5e");
  }

  @Test
  void splitsIntoVendorAndDevicePart() {
    MacAddress mac = MacAddress.parse("00:1a:2b:3c:4d:5e");

    assertThat(mac.oui()).isEqualTo("00:1a:2b");
    assertThat(mac.deviceIdentifier()).isEqualTo("3c:4d:5e");
  }

  @Test
  void convertsToAndFromBytes() {
    MacAddress mac = new MacAddress(EXPECTED);

    assertThat(mac.toBytes()).containsExactly(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0x5E);
    assertThat(MacAddress.fromBytes(mac.toBytes())).isEqualTo(mac);
    assertThat(MacAddress.fromBytes(new byte[] {(byte) 0xFF, 0, 0, 0, 0, (byte) 0x80}).value())
        .isEqualTo(0xFF00_0000_0080L);
    assertThatThrownBy(() -> MacAddress.fromBytes(new byte[5]))
        .isInstanceOf(InvalidAddressException.class)
        .hasMessageContaining("6 bytes");
    assertThatNullPointerException().isThrownBy(() -> MacAddress.fromBytes(null));
  }

  @Test
  void toBytesReturnsACopy() {
    MacAddress mac = new MacAddress(EXPECTED);

    mac.toBytes()[0] = 0x7F;

    assertThat(mac.toBytes()[0]).isZero();
  }

  @ParameterizedTest
  @CsvSource({
    "ff:ff:ff:ff:ff:ff, true",
    "FF-FF-FF-FF-FF-FF, true",
    "ffff.ffff.ffff, true",
    "ff:ff:ff:ff:ff:fe, false",
    "00:00:00:00:00:00, false",
    "fe:ff:ff:ff:ff:ff, false"
  })
  void recognisesTheBroadcastAddress(String text, boolean expected) {
    assertThat(MacAddress.parse(text).isBroadcast()).isEqualTo(expected);
  }

  @Test
  void theBroadcastConstantIsAllOnes() {
    assertThat(MacAddress.BROADCAST).hasToString("ff:ff:ff:ff:ff:ff");
    assertThat(MacAddress.BROADCAST.isBroadcast()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "01:00:5e:00:00:01, true",
    "33:33:00:00:00:01, true",
    "ff:ff:ff:ff:ff:ff, true",
    "00:1a:2b:3c:4d:5e, false",
    "02:00:00:00:00:01, false",
    "fe:00:00:00:00:00, false"
  })
  void theFirstBitOfTheFirstByteMarksGroupAddresses(String text, boolean multicast) {
    MacAddress mac = MacAddress.parse(text);

    assertThat(mac.isMulticast()).isEqualTo(multicast);
    assertThat(mac.isUnicast()).isEqualTo(!multicast);
  }

  @ParameterizedTest
  @CsvSource({
    "02:00:00:00:00:01, true",
    "fe:00:00:00:00:00, true",
    "06:00:00:00:00:00, true",
    "00:1a:2b:3c:4d:5e, false",
    "01:00:5e:00:00:01, false",
    "fc:00:00:00:00:00, false"
  })
  void theSecondBitOfTheFirstByteMarksLocallyAdministeredAddresses(String text, boolean local) {
    assertThat(MacAddress.parse(text).isLocallyAdministered()).isEqualTo(local);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "00:1a:2b:3c:4d",
        "00:1a:2b:3c:4d:5e:6f",
        "00:1a:2b:3c:4d:5g",
        "00:1a-2b:3c:4d:5e",
        "00:1a:2b-3c-4d-5e",
        "001a.2b3c.4d5",
        "001a.2b3c.4d5e.6f70",
        "001a:2b3c:4d5e",
        "00-1a-2b-3c-4d-5",
        "0:1:2:3:4:5",
        "001a2b3c4d5e",
        " 00:1a:2b:3c:4d:5e",
        "00:1a:2b:3c:4d:5e ",
        "٠٠:1a:2b:3c:4d:5e",
        "00:1a:2b:3c:4d:5e:00:1a:2b:3c:4d:5e:00:1a:2b:3c:4d:5e"
      })
  void rejectsMalformedText(String text) {
    assertThatThrownBy(() -> MacAddress.parse(text))
        .isInstanceOf(InvalidAddressException.class)
        .hasMessageContaining("not a valid MAC address");
  }

  @Test
  void rejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> MacAddress.parse(null));
    MacAddress mac = new MacAddress(0);
    assertThatNullPointerException().isThrownBy(() -> mac.format(null));
  }

  @Test
  void aMalformedMessageShowsTheAcceptedStyles() {
    assertThatThrownBy(() -> MacAddress.parse("00:1a:2b"))
        .hasMessageContaining("00:1a:2b:3c:4d:5e")
        .hasMessageContaining("001a.2b3c.4d5e");
  }

  @ParameterizedTest
  @ValueSource(longs = {-1L, 0x1_0000_0000_0000L, Long.MIN_VALUE, Long.MAX_VALUE})
  void rejectsAValueOutsideFortyEightBits(long value) {
    assertThatThrownBy(() -> new MacAddress(value)).isInstanceOf(InvalidAddressException.class);
  }

  @Test
  void acceptsTheExtremes() {
    assertThat(new MacAddress(0)).hasToString("00:00:00:00:00:00");
    assertThat(new MacAddress(0xFFFF_FFFF_FFFFL)).isEqualTo(MacAddress.BROADCAST);
  }

  @ParameterizedTest
  @EnumSource(MacStyle.class)
  void everyStyleRoundTripsForRandomAddresses(MacStyle style) {
    Random random = new Random(SEED);
    for (int i = 0; i < 10_000; i++) {
      MacAddress mac = new MacAddress(random.nextLong() & 0xFFFF_FFFF_FFFFL);
      String context = "seed " + SEED + ", iteration " + i + ", " + mac + ", " + style;

      assertThat(MacAddress.parse(mac.format(style))).as(context).isEqualTo(mac);
      assertThat(MacAddress.parse(mac.format(style, true))).as(context).isEqualTo(mac);
      assertThat(MacAddress.fromBytes(mac.toBytes())).as(context).isEqualTo(mac);
      assertThat(mac.oui() + ":" + mac.deviceIdentifier()).as(context).isEqualTo(mac.toString());
      assertThat(mac.isMulticast()).as(context).isEqualTo((mac.toBytes()[0] & 1) == 1);
      assertThat(mac.isBroadcast()).as(context).isEqualTo(mac.value() == 0xFFFF_FFFF_FFFFL);
    }
  }
}
