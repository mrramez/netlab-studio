package io.github.mrramez.netlabstudio.ui;

import java.util.Locale;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The home screen: one card for each main area of the app (SPEC §4). The areas are placeholders
 * until their phases are built.
 */
final class HomeView extends ScrollPane {

  private static final int COLUMNS = 3;

  /** The main areas, in the order SPEC §4 lists them. */
  private enum Area {
    LESSONS(Icons.LESSONS),
    SIMULATOR(Icons.SIMULATOR),
    SUBNETTING(Icons.SUBNETTING),
    CLI(Icons.CLI),
    SCENARIOS(Icons.SCENARIOS),
    PROGRESS(Icons.PROGRESS);

    private final String icon;

    Area(String icon) {
      this.icon = icon;
    }

    String messageKey(String suffix) {
      return "home.card." + name().toLowerCase(Locale.ROOT) + "." + suffix;
    }
  }

  HomeView(I18n i18n) {
    getStyleClass().add("home-scroll");
    setFitToWidth(true);

    Label subtitle = Labels.bound(i18n.text("home.subtitle"), "home-subtitle");
    subtitle.setWrapText(true);

    GridPane grid = new GridPane();
    grid.getStyleClass().add("home-grid");
    for (int column = 0; column < COLUMNS; column++) {
      ColumnConstraints constraints = new ColumnConstraints();
      constraints.setPercentWidth(100.0 / COLUMNS);
      grid.getColumnConstraints().add(constraints);
    }
    Area[] areas = Area.values();
    for (int i = 0; i < areas.length; i++) {
      grid.add(card(i18n, areas[i]), i % COLUMNS, i / COLUMNS);
    }

    VBox content = new VBox(Labels.bound(i18n.text("home.title"), "home-title"), subtitle, grid);
    content.getStyleClass().add("home");
    setContent(content);
  }

  private static VBox card(I18n i18n, Area area) {
    Label description =
        Labels.bound(i18n.text(area.messageKey("description")), "home-card-description");
    description.setWrapText(true);

    // The badge says in words, not only by styling, that the area is not available yet.
    Label badge = Labels.bound(i18n.text("home.card.comingSoon"), "badge");
    badge.setMaxWidth(Region.USE_PREF_SIZE);

    VBox card =
        new VBox(
            Icons.create(area.icon, 40, "home-card-icon"),
            Labels.bound(i18n.text(area.messageKey("title")), "home-card-title"),
            description,
            badge);
    card.getStyleClass().add("home-card");
    return card;
  }
}
