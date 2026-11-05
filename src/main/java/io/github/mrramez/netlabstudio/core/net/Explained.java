package io.github.mrramez.netlabstudio.core.net;

import java.util.List;
import java.util.Objects;

/**
 * A calculation result together with the steps that lead to it, so the UI can show a worked
 * solution instead of just the answer.
 *
 * @param result the answer
 * @param steps the worked solution, in reading order
 * @param <T> the type of the answer
 */
public record Explained<T>(T result, List<ExplanationStep> steps) {

  /** Rejects a missing result and copies the steps. */
  public Explained {
    Objects.requireNonNull(result, "result");
    steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
  }
}
