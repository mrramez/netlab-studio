package io.github.mrramez.netlabstudio.ui;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.util.List;
import javafx.scene.text.Font;

/**
 * Registers the bundled Noto Sans and Noto Sans Arabic UI fonts (SIL Open Font License 1.1) with
 * JavaFX, so that stylesheets can use them by family name.
 *
 * <p>Shipping the fonts makes text, and Arabic text in particular, look the same on every computer
 * instead of depending on which fonts are installed. The Arabic font is the "UI" member of the Noto
 * Sans Arabic family. Its line height matches Noto Sans (1.36 em), whereas the standard design
 * needs 2.11 em, which would make Arabic labels and controls taller than the English ones.
 */
public final class BundledFonts {

  static final String DIRECTORY = "/fonts/";

  static final List<String> FILES =
      List.of(
          "NotoSans-Regular.ttf",
          "NotoSans-Bold.ttf",
          "NotoSansArabicUI-Regular.ttf",
          "NotoSansArabicUI-Bold.ttf");

  // Only affects the Font object that loadFont returns, which is not used.
  private static final double ANY_SIZE = 14;

  private static final System.Logger LOG = System.getLogger(BundledFonts.class.getName());

  private BundledFonts() {}

  /**
   * Loads every bundled font. Call once, on the JavaFX application thread, before building any
   * scene. A font that fails to load is logged and skipped, and its text falls back to a system
   * font: the app still works.
   */
  public static void loadAll() {
    for (String file : FILES) {
      try (InputStream in = BundledFonts.class.getResourceAsStream(DIRECTORY + file)) {
        if (in == null || Font.loadFont(in, ANY_SIZE) == null) {
          LOG.log(Level.WARNING, "Could not load bundled font {0}", file);
        }
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Could not read bundled font " + file, e);
      }
    }
  }
}
