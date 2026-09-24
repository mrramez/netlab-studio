package io.github.mrramez.netlabstudio.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BundledFontsTest {

  // "sfnt" version 1.0: a TrueType font. A line-ending conversion or a Git LFS pointer file would
  // not start with these bytes.
  private static final byte[] TRUETYPE_SIGNATURE = {0x00, 0x01, 0x00, 0x00};

  static Stream<String> fontFiles() {
    return BundledFonts.FILES.stream();
  }

  @ParameterizedTest
  @MethodSource("fontFiles")
  void fontIsBundledAsATrueTypeFile(String file) throws IOException {
    try (InputStream in =
        BundledFontsTest.class.getResourceAsStream(BundledFonts.DIRECTORY + file)) {
      assertThat(in).as(file).isNotNull();
      assertThat(in.readNBytes(TRUETYPE_SIGNATURE.length)).isEqualTo(TRUETYPE_SIGNATURE);
    }
  }

  // The OFL requires the licence to be distributed with the fonts.
  @ParameterizedTest
  @ValueSource(strings = {"OFL-NotoSans.txt", "OFL-NotoSansArabicUI.txt"})
  void fontLicenceIsBundled(String file) throws IOException {
    try (InputStream in =
        BundledFontsTest.class.getResourceAsStream(BundledFonts.DIRECTORY + file)) {
      assertThat(in).as(file).isNotNull();
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
          .contains("The Noto Project Authors")
          .contains("SIL OPEN FONT LICENSE Version 1.1");
    }
  }
}
