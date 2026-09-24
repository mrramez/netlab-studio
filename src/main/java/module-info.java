/**
 * NetLab Studio: interactive lessons, a network simulator, a subnetting trainer and a simplified
 * Cisco IOS CLI for learning computer-networking fundamentals.
 *
 * <p>All logic lives in the {@code core} packages, which never use JavaFX. {@code ui} renders that
 * logic and forwards user input, and {@code app} wires everything together.
 */
module io.github.mrramez.netlabstudio {
  requires javafx.controls;
  requires javafx.fxml;
}
