package io.github.mrramez.netlabstudio.core.net;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One line of a worked solution, as data: a message key and its arguments, never display text.
 *
 * <p>The UI looks the key up in the English or Arabic message bundle and fills in the arguments, so
 * the same calculation can be explained in either language. Arguments are immutable values ({@link
 * String}, {@link Integer}, {@link Long} or {@link Boolean}); addresses are passed in their text
 * form and are shown left to right by the UI.
 *
 * @param key dotted message key such as {@code net.subnet.borrowBits}
 * @param args values substituted into the message, in order
 */
public record ExplanationStep(String key, List<Object> args) {

  // Lower-case first letter per segment keeps keys uniform and easy to find in the bundles.
  private static final Pattern KEY = Pattern.compile("[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)+");

  /** Validates the key and copies the arguments, so a step can never change after creation. */
  public ExplanationStep {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(args, "args");
    if (!KEY.matcher(key).matches()) {
      throw new IllegalArgumentException(
          "Explanation key '" + key + "' must be dotted segments such as 'net.subnet.borrowBits'");
    }
    args = List.copyOf(args);
    for (int i = 0; i < args.size(); i++) {
      Object arg = args.get(i);
      if (!(arg instanceof String
          || arg instanceof Integer
          || arg instanceof Long
          || arg instanceof Boolean)) {
        throw new IllegalArgumentException(
            "Argument "
                + i
                + " of step '"
                + key
                + "' has unsupported type "
                + arg.getClass().getSimpleName()
                + " (allowed: String, Integer, Long, Boolean)");
      }
    }
  }

  /** Creates a step from a key and its arguments. */
  public static ExplanationStep of(String key, Object... args) {
    return new ExplanationStep(key, args == null ? null : Arrays.asList(args));
  }
}
