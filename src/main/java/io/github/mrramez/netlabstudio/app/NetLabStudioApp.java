package io.github.mrramez.netlabstudio.app;

import io.github.mrramez.netlabstudio.ui.BundledFonts;
import io.github.mrramez.netlabstudio.ui.I18n;
import io.github.mrramez.netlabstudio.ui.Language;
import io.github.mrramez.netlabstudio.ui.MainWindow;
import io.github.mrramez.netlabstudio.ui.Theme;
import io.github.mrramez.netlabstudio.ui.ThemeManager;
import java.util.Locale;
import javafx.application.Application;
import javafx.stage.Stage;

/** The JavaFX application: creates the services and opens the main window. */
public final class NetLabStudioApp extends Application {

  /** Creates the application; JavaFX calls this reflectively. */
  public NetLabStudioApp() {}

  @Override
  public void start(Stage stage) {
    BundledFonts.loadAll();
    I18n i18n = new I18n(Language.forLocale(Locale.getDefault()));
    ThemeManager themes = new ThemeManager(Theme.LIGHT);
    new MainWindow(i18n, themes).show(stage);
  }
}
