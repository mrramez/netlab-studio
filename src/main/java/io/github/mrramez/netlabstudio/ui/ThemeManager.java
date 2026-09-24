package io.github.mrramez.netlabstudio.ui;

import java.net.URL;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;

/** Holds the selected {@link Theme} and keeps a scene's stylesheets in step with it. */
public final class ThemeManager {

  /** Theme-independent component styles, loaded together with every theme. */
  static final String APP_STYLESHEET = "/css/app.css";

  private final ObjectProperty<Theme> theme;

  /** Creates the manager, starting with {@code initialTheme}. */
  public ThemeManager(Theme initialTheme) {
    theme = new SimpleObjectProperty<>(this, "theme", Objects.requireNonNull(initialTheme));
  }

  /** The selected theme. */
  public ObjectProperty<Theme> themeProperty() {
    return theme;
  }

  public Theme getTheme() {
    return theme.get();
  }

  public void setTheme(Theme newTheme) {
    theme.set(Objects.requireNonNull(newTheme));
  }

  /** Styles {@code scene} with the selected theme now and whenever the theme changes. */
  public void attach(Scene scene) {
    apply(scene, theme.get());
    theme.addListener((observable, oldTheme, newTheme) -> apply(scene, newTheme));
  }

  private static void apply(Scene scene, Theme theme) {
    scene.getStylesheets().setAll(url(APP_STYLESHEET), url(theme.stylesheet()));
  }

  private static String url(String resource) {
    URL url = ThemeManager.class.getResource(resource);
    if (url == null) {
      throw new IllegalStateException("Stylesheet not found: " + resource);
    }
    return url.toExternalForm();
  }
}
