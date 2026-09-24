package io.github.mrramez.netlabstudio.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/**
 * A row of mutually exclusive toggle buttons bound to a property. Selecting a button sets the
 * property, and changing the property selects the matching button.
 *
 * <p>Each button has the style class {@code option-<constant name in lower case>}, for example
 * {@code option-arabic}, so CSS can style an individual option.
 *
 * @param <T> the type of the options
 */
final class SegmentedToggle<T extends Enum<T>> extends HBox {

  private final ToggleGroup group = new ToggleGroup();
  private final Map<Toggle, T> options = new HashMap<>();

  SegmentedToggle(
      ObjectProperty<T> property,
      List<T> values,
      Function<T, ObservableValue<String>> label,
      ObservableValue<String> tooltip) {
    getStyleClass().add("segmented");
    for (T value : values) {
      ToggleButton button = new ToggleButton();
      button.getStyleClass().add("option-" + value.name().toLowerCase(Locale.ROOT));
      button.textProperty().bind(label.apply(value));
      Tooltip buttonTooltip = new Tooltip();
      buttonTooltip.textProperty().bind(tooltip);
      button.setTooltip(buttonTooltip);
      button.setToggleGroup(group);
      options.put(button, value);
      getChildren().add(button);
    }
    // The outer ends get rounded corners.
    getChildren().getFirst().getStyleClass().add("first");
    getChildren().getLast().getStyleClass().add("last");

    select(property.get());
    property.addListener((observable, oldValue, newValue) -> select(newValue));
    group
        .selectedToggleProperty()
        .addListener(
            (observable, oldToggle, newToggle) -> {
              if (newToggle == null) {
                // Clicking the selected button would clear the selection; one option must stay on.
                group.selectToggle(oldToggle);
              } else {
                property.set(options.get(newToggle));
              }
            });
  }

  private void select(T value) {
    options.forEach(
        (toggle, option) -> {
          if (option.equals(value)) {
            group.selectToggle(toggle);
          }
        });
  }
}
