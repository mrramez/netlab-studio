package io.github.mrramez.netlabstudio.core.net;

/**
 * Thrown when text or numbers do not describe a valid IPv4 address, subnet mask, network or MAC
 * address. The message is written for developers and names the offending input and the rule it
 * breaks; the UI shows its own translated text instead.
 */
public final class InvalidAddressException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  // Messages quote user input, so a huge paste must not turn into a huge message.
  private static final int MAX_QUOTED_LENGTH = 40;

  /** Creates the exception with a message. */
  public InvalidAddressException(String message) {
    super(message);
  }

  /** The input in single quotes, shortened when it is very long. */
  static String quoted(String input) {
    if (input.length() > MAX_QUOTED_LENGTH) {
      return "'" + input.substring(0, MAX_QUOTED_LENGTH) + "...'";
    }
    return "'" + input + "'";
  }
}
