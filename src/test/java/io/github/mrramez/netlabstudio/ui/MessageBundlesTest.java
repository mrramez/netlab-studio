package io.github.mrramez.netlabstudio.ui;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Keeps the English and Arabic message bundles complete and consistent (CLAUDE.md, rule 5). */
class MessageBundlesTest {

  private static final String ENGLISH = "/i18n/messages_en.properties";
  private static final String ARABIC = "/i18n/messages_ar.properties";

  @Test
  void bothLanguagesHaveExactlyTheSameKeys() throws IOException {
    assertThat(load(ARABIC).stringPropertyNames())
        .as("keys of %s compared with %s", ARABIC, ENGLISH)
        .containsExactlyInAnyOrderElementsOf(load(ENGLISH).stringPropertyNames());
  }

  @ParameterizedTest
  @ValueSource(strings = {ENGLISH, ARABIC})
  void isValidUtf8(String bundle) {
    assertThatCode(() -> read(bundle)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {ENGLISH, ARABIC})
  void hasNoBlankMessage(String bundle) throws IOException {
    Properties messages = load(bundle);
    assertThat(messages.stringPropertyNames())
        .allSatisfy(key -> assertThat(messages.getProperty(key)).as(key).isNotBlank());
  }

  // java.util.Properties silently keeps only the last of two identical keys.
  @ParameterizedTest
  @ValueSource(strings = {ENGLISH, ARABIC})
  void definesEveryKeyOnlyOnce(String bundle) throws IOException {
    Map<String, Long> occurrences =
        keysInOrder(read(bundle)).stream().collect(groupingBy(key -> key, counting()));
    assertThat(occurrences)
        .allSatisfy((key, count) -> assertThat(count).as("definitions of %s", key).isOne());
  }

  private static Properties load(String bundle) throws IOException {
    Properties messages = new Properties();
    messages.load(new StringReader(read(bundle)));
    return messages;
  }

  private static List<String> keysInOrder(String text) {
    return text.lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty() && !line.startsWith("#") && !line.startsWith("!"))
        .map(line -> line.substring(0, line.indexOf('=')).strip())
        .toList();
  }

  // Decodes strictly: Java would silently read a bundle that is not valid UTF-8 as ISO-8859-1,
  // turning the Arabic text into garbage.
  private static String read(String bundle) throws IOException {
    try (InputStream in = MessageBundlesTest.class.getResourceAsStream(bundle)) {
      assertThat(in).as(bundle).isNotNull();
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(in.readAllBytes()))
          .toString();
    }
  }
}
