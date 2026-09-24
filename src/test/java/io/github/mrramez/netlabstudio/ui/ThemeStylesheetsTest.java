package io.github.mrramez.netlabstudio.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Keeps the themes interchangeable, and the OSI layer colours defined exactly once with the values
 * in CLAUDE.md, rule 7.
 */
class ThemeStylesheetsTest {

  private static final String OSI_PALETTE = "/css/osi-layers.css";

  private static final Map<String, String> OSI_COLOURS =
      Map.of(
          "-osi-l7-application", "#8E44AD",
          "-osi-l6-presentation", "#9B59B6",
          "-osi-l5-session", "#3498DB",
          "-osi-l4-transport", "#2E86C1",
          "-osi-l3-network", "#27AE60",
          "-osi-l2-data-link", "#F39C12",
          "-osi-l1-physical", "#E74C3C");

  private static final Pattern THEME_VARIABLE_DEFINITION = Pattern.compile("(-nl-[a-z0-9-]+)\\s*:");
  // Possessive quantifier: a definition such as "-nl-bg:" must not match as a use of "-nl-b".
  private static final Pattern THEME_VARIABLE_USE = Pattern.compile("(-nl-[a-z0-9-]++)(?!\\s*:)");
  private static final Pattern OSI_DEFINITION =
      Pattern.compile("(-osi-[a-z0-9-]+)\\s*:\\s*(#[0-9A-Fa-f]{6})\\s*;");

  @Test
  void lightAndDarkDefineTheSameVariables() {
    assertThat(matches(THEME_VARIABLE_DEFINITION, css(Theme.DARK.stylesheet())))
        .isEqualTo(matches(THEME_VARIABLE_DEFINITION, css(Theme.LIGHT.stylesheet())));
  }

  @ParameterizedTest
  @EnumSource(Theme.class)
  void themeDefinesEveryVariableThatAppCssUses(Theme theme) {
    assertThat(matches(THEME_VARIABLE_DEFINITION, css(theme.stylesheet())))
        .containsAll(matches(THEME_VARIABLE_USE, css(ThemeManager.APP_STYLESHEET)));
  }

  @ParameterizedTest
  @EnumSource(Theme.class)
  void themeImportsTheOsiPalette(Theme theme) {
    assertThat(css(theme.stylesheet())).contains("@import \"osi-layers.css\";");
  }

  @Test
  void osiPaletteHasExactlyTheColoursFromClaudeMd() {
    Map<String, String> defined = new HashMap<>();
    Matcher matcher = OSI_DEFINITION.matcher(css(OSI_PALETTE));
    while (matcher.find()) {
      defined.put(matcher.group(1), matcher.group(2).toUpperCase(Locale.ROOT));
    }
    assertThat(defined).isEqualTo(OSI_COLOURS);
  }

  @ParameterizedTest
  @ValueSource(strings = {ThemeManager.APP_STYLESHEET, "/css/light.css", "/css/dark.css"})
  void osiColoursAreDefinedNowhereElse(String stylesheet) {
    String text = css(stylesheet);
    assertThat(text).doesNotContain("-osi-l");
    assertThat(OSI_COLOURS.values()).noneMatch(hex -> text.toUpperCase(Locale.ROOT).contains(hex));
  }

  private static Set<String> matches(Pattern pattern, String text) {
    Set<String> names = new TreeSet<>();
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  /** The stylesheet's text without comments. */
  private static String css(String resource) {
    try (InputStream in = ThemeStylesheetsTest.class.getResourceAsStream(resource)) {
      assertThat(in).as(resource).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .replaceAll("(?s)/\\*.*?\\*/", "");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
