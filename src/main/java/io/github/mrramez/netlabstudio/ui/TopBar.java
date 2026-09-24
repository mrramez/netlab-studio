package io.github.mrramez.netlabstudio.ui;

import java.util.List;
import java.util.Locale;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** The top bar, always visible: app name, language toggle and theme toggle (SPEC §4). */
final class TopBar extends HBox {

  TopBar(I18n i18n, ThemeManager themes) {
    getStyleClass().add("top-bar");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    SegmentedToggle<Language> languageToggle =
        new SegmentedToggle<>(
            i18n.languageProperty(),
            List.of(Language.ENGLISH, Language.ARABIC),
            language -> i18n.text("language." + language.name().toLowerCase(Locale.ROOT)),
            i18n.text("topbar.language.tooltip"));

    SegmentedToggle<Theme> themeToggle =
        new SegmentedToggle<>(
            themes.themeProperty(),
            List.of(Theme.LIGHT, Theme.DARK),
            theme -> i18n.text("theme." + theme.name().toLowerCase(Locale.ROOT)),
            i18n.text("topbar.theme.tooltip"));

    getChildren()
        .addAll(
            Icons.create(Icons.LOGO, 28, "app-logo"),
            Labels.bound(i18n.text("app.name"), "app-name"),
            spacer,
            Labels.bound(i18n.text("topbar.language"), "top-bar-label"),
            languageToggle,
            Labels.bound(i18n.text("topbar.theme"), "top-bar-label", "top-bar-group"),
            themeToggle);
  }
}
