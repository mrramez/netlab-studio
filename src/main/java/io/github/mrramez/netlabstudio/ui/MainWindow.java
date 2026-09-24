package io.github.mrramez.netlabstudio.ui;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** The main window: the top bar above the current screen. Only the home screen exists so far. */
public final class MainWindow {

  static final double DEFAULT_WIDTH = 1280;
  static final double DEFAULT_HEIGHT = 800;
  static final double MIN_WIDTH = 1100;
  static final double MIN_HEIGHT = 700;

  /** Set on the root node while Arabic is selected, so that CSS can switch to the Arabic font. */
  private static final PseudoClass ARABIC = PseudoClass.getPseudoClass("arabic");

  private final I18n i18n;
  private final ThemeManager themes;

  /** Creates the window for the given text and theme services. */
  public MainWindow(I18n i18n, ThemeManager themes) {
    this.i18n = Objects.requireNonNull(i18n);
    this.themes = Objects.requireNonNull(themes);
  }

  /** Builds the window content, sizes the window and shows it. */
  public void show(Stage stage) {
    BorderPane root = new BorderPane();
    root.setTop(new TopBar(i18n, themes));
    root.setCenter(new HomeView(i18n));

    Scene scene = new Scene(root);
    scene
        .nodeOrientationProperty()
        .bind(
            Bindings.createObjectBinding(
                () -> i18n.getLanguage().orientation(), i18n.languageProperty()));
    root.pseudoClassStateChanged(ARABIC, i18n.getLanguage() == Language.ARABIC);
    i18n.languageProperty()
        .addListener(
            (observable, oldLanguage, newLanguage) ->
                root.pseudoClassStateChanged(ARABIC, newLanguage == Language.ARABIC));
    themes.attach(scene);

    // On a small screen the default size would overflow, so never exceed the usable area.
    Rectangle2D screen = Screen.getPrimary().getVisualBounds();
    stage.setWidth(Math.min(DEFAULT_WIDTH, screen.getWidth()));
    stage.setHeight(Math.min(DEFAULT_HEIGHT, screen.getHeight()));
    stage.setMinWidth(MIN_WIDTH);
    stage.setMinHeight(MIN_HEIGHT);
    stage.titleProperty().bind(i18n.text("app.name"));
    stage.setScene(scene);
    stage.show();
  }
}
