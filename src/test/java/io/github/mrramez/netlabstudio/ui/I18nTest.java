package io.github.mrramez.netlabstudio.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.NodeOrientation;
import org.junit.jupiter.api.Test;

class I18nTest {

  @Test
  void textFollowsTheSelectedLanguage() {
    I18n i18n = new I18n(Language.ENGLISH);
    StringBinding title = i18n.text("home.card.lessons.title");
    assertThat(title.get()).isEqualTo("Lessons");

    i18n.setLanguage(Language.ARABIC);
    assertThat(title.get()).isEqualTo("الدروس");

    i18n.setLanguage(Language.ENGLISH);
    assertThat(title.get()).isEqualTo("Lessons");
  }

  @Test
  void boundPropertiesUpdateLiveWhenTheLanguageChanges() {
    I18n i18n = new I18n(Language.ENGLISH);
    StringProperty labelText = new SimpleStringProperty();
    labelText.bind(i18n.text("theme.dark"));
    assertThat(labelText.get()).isEqualTo("Dark");

    i18n.setLanguage(Language.ARABIC);

    assertThat(labelText.get()).isEqualTo("داكن");
  }

  @Test
  void arabicIsLaidOutRightToLeftAndEnglishLeftToRight() {
    assertThat(Language.ARABIC.orientation()).isEqualTo(NodeOrientation.RIGHT_TO_LEFT);
    assertThat(Language.ENGLISH.orientation()).isEqualTo(NodeOrientation.LEFT_TO_RIGHT);
  }

  @Test
  void startsInTheSystemLanguageWhenTheAppHasIt() {
    assertThat(Language.forLocale(Locale.forLanguageTag("ar-YE"))).isEqualTo(Language.ARABIC);
    assertThat(Language.forLocale(Locale.forLanguageTag("en-GB"))).isEqualTo(Language.ENGLISH);
    assertThat(Language.forLocale(Locale.FRENCH)).isEqualTo(Language.ENGLISH);
  }

  @Test
  void missingKeyShowsAMarkerInsteadOfFailing() {
    assertThat(new I18n(Language.ENGLISH).get("no.such.key")).isEqualTo("!no.such.key!");
  }
}
