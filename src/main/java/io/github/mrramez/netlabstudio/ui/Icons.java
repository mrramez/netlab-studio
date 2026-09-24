package io.github.mrramez.netlabstudio.ui;

import javafx.geometry.NodeOrientation;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * Original line icons, drawn as SVG path data on a 24 × 24 grid. Colour and stroke width come from
 * CSS (style class {@code icon}).
 */
final class Icons {

  /** App mark: an isometric block, the "lab" in NetLab. */
  static final String LOGO =
      "M12 3L19.8 7.5V16.5L12 21L4.2 16.5V7.5ZM4.2 7.5L12 12L19.8 7.5M12 12V21";

  /** Lessons: an open book. */
  static final String LESSONS =
      "M12 6.5C10.2 5.2 7.2 4.8 3.5 5V18.5C7.2 18.3 10.2 18.7 12 20C13.8 18.7 16.8 18.3 20.5 18.5"
          + "V5C16.8 4.8 13.8 5.2 12 6.5ZM12 6.5V20";

  /** Simulator: three hosts cabled to a switch. */
  static final String SIMULATOR =
      "M9 10.5H15V13.5H9ZM2.5 5A2 2 0 1 0 6.5 5A2 2 0 1 0 2.5 5ZM17.5 5A2 2 0 1 0 21.5 5"
          + "A2 2 0 1 0 17.5 5ZM10 20A2 2 0 1 0 14 20A2 2 0 1 0 10 20ZM5.9 6.5L9.4 10.5"
          + "M18.1 6.5L14.6 10.5M12 13.5V18";

  /** Subnetting Lab: a four-octet address with the network part underlined. */
  static final String SUBNETTING = "M3 7H21V17H3ZM7.5 7V17M12 7V17M16.5 7V17M3 20.5H16.5";

  /** CLI Practice: a terminal window with a prompt. */
  static final String CLI = "M3 5H21V19H3ZM3 8.5H21M7 12L9.5 14L7 16M11.5 16H16";

  /** Scenarios: a goal flag. */
  static final String SCENARIOS = "M5 21V3.5M5 4.5H17.5L15 8.5L17.5 12.5H5";

  /** Progress: rising bars. */
  static final String PROGRESS =
      "M3.5 20.5H20.5M5.5 20.5V14H9V20.5M10.5 20.5V10H14V20.5M15.5 20.5V5.5H19V20.5";

  private static final double GRID = 24;

  private Icons() {}

  /**
   * Creates an icon {@code size} pixels square.
   *
   * <p>Icons are not mirrored in right-to-left layouts; the CLI prompt, for example, must keep
   * pointing to the right.
   */
  static Node create(String pathData, double size, String styleClass) {
    SVGPath path = new SVGPath();
    path.setContent(pathData);
    path.getStyleClass().addAll("icon", styleClass);

    // Transparent square so every icon has the same bounds, whatever the extent of its path.
    Rectangle grid = new Rectangle(GRID, GRID, Color.TRANSPARENT);

    Group scaled = new Group(grid, path);
    scaled.getTransforms().add(new Scale(size / GRID, size / GRID));

    Group icon = new Group(scaled);
    icon.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
    return icon;
  }
}
