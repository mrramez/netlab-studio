package io.github.mrramez.netlabstudio.app;

import javafx.application.Application;

/**
 * Command-line entry point.
 *
 * <p>It is separate from {@link NetLabStudioApp} because the Java launcher refuses to start a main
 * class that extends {@code Application} when JavaFX is on the class path rather than the module
 * path, as in some IDE run configurations.
 */
public final class Main {

  private Main() {}

  /**
   * Starts NetLab Studio.
   *
   * @param args command-line arguments, passed on to JavaFX
   */
  public static void main(String[] args) {
    Application.launch(NetLabStudioApp.class, args);
  }
}
