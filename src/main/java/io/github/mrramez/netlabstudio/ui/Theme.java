package io.github.mrramez.netlabstudio.ui;

/**
 * Colour themes. A theme stylesheet only defines colour variables; the shared component styles in
 * {@code app.css} use them.
 */
public enum Theme {
  LIGHT("/css/light.css"),
  DARK("/css/dark.css");

  private final String stylesheet;

  Theme(String stylesheet) {
    this.stylesheet = stylesheet;
  }

  /** The class-path location of this theme's stylesheet. */
  String stylesheet() {
    return stylesheet;
  }
}
