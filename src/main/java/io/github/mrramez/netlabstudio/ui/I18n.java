package io.github.mrramez.netlabstudio.ui;

import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * User-visible text in the selected {@link Language}.
 *
 * <p>Views bind to {@link #text(String)} rather than reading a string once. Switching the language
 * then updates every label in place, without rebuilding the scene or losing what is on screen.
 */
public final class I18n {

  static final String BUNDLE_BASE_NAME = "i18n.messages";

  private static final System.Logger LOG = System.getLogger(I18n.class.getName());

  private final ObjectProperty<Language> language;
  private final ObjectBinding<ResourceBundle> messages;

  /** Creates the text source, starting in {@code initialLanguage}. */
  public I18n(Language initialLanguage) {
    language =
        new SimpleObjectProperty<>(this, "language", Objects.requireNonNull(initialLanguage));
    messages =
        Bindings.createObjectBinding(
            () -> ResourceBundle.getBundle(BUNDLE_BASE_NAME, language.get().locale()), language);
  }

  /** The selected language. */
  public ObjectProperty<Language> languageProperty() {
    return language;
  }

  public Language getLanguage() {
    return language.get();
  }

  public void setLanguage(Language newLanguage) {
    language.set(Objects.requireNonNull(newLanguage));
  }

  /** The text for {@code key} in the selected language, updated whenever the language changes. */
  public StringBinding text(String key) {
    Objects.requireNonNull(key);
    return Bindings.createStringBinding(() -> lookUp(key), messages);
  }

  /** The text for {@code key} in the selected language, as it is now. */
  public String get(String key) {
    return lookUp(Objects.requireNonNull(key));
  }

  // A missing key shows up as "!key!" instead of an exception, so one forgotten string cannot stop
  // the whole UI, yet is easy to spot. MessageBundlesTest keeps the two languages in sync.
  private String lookUp(String key) {
    ResourceBundle bundle = messages.get();
    if (bundle.containsKey(key)) {
      return bundle.getString(key);
    }
    LOG.log(Level.WARNING, "No message for key \"{0}\" ({1})", key, bundle.getLocale());
    return "!" + key + "!";
  }
}
