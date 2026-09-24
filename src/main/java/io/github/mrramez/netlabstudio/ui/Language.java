package io.github.mrramez.netlabstudio.ui;

import java.util.Locale;
import javafx.geometry.NodeOrientation;

/** The languages of the user interface. Arabic is laid out right to left. */
public enum Language {
  ENGLISH(Locale.ENGLISH, NodeOrientation.LEFT_TO_RIGHT),
  ARABIC(Locale.forLanguageTag("ar"), NodeOrientation.RIGHT_TO_LEFT);

  private final Locale locale;
  private final NodeOrientation orientation;

  Language(Locale locale, NodeOrientation orientation) {
    this.locale = locale;
    this.orientation = orientation;
  }

  /** The locale used to look up this language's messages. */
  public Locale locale() {
    return locale;
  }

  /** The layout direction for this language. */
  public NodeOrientation orientation() {
    return orientation;
  }

  /**
   * The interface language matching {@code locale}, or English when there is no translation for it.
   */
  public static Language forLocale(Locale locale) {
    for (Language language : values()) {
      if (language.locale.getLanguage().equals(locale.getLanguage())) {
        return language;
      }
    }
    return ENGLISH;
  }
}
