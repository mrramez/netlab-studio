package io.github.mrramez.netlabstudio.core.archfixture;

import io.github.mrramez.netlabstudio.ui.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Deliberately breaks both core layering rules. {@code ArchitectureTest} uses it to prove that the
 * rules really reject such code. It lives only in the test sources and never ships.
 */
final class LayeringViolation {

  private LayeringViolation() {}

  static StringProperty usesJavaFx() {
    return new SimpleStringProperty();
  }

  static String usesUi(I18n i18n) {
    return i18n.get("app.name");
  }
}
