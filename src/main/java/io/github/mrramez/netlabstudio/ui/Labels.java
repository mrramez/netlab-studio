package io.github.mrramez.netlabstudio.ui;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;

/** Factory for labels whose text follows the selected language. */
final class Labels {

  private Labels() {}

  /** A label bound to {@code text}, typically from {@link I18n#text(String)}. */
  static Label bound(ObservableValue<String> text, String... styleClasses) {
    Label label = new Label();
    label.textProperty().bind(text);
    label.getStyleClass().addAll(styleClasses);
    return label;
  }
}
