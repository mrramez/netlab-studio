package io.github.mrramez.netlabstudio.core.net;

/** The three ways MAC addresses are commonly written. */
public enum MacStyle {
  /** {@code 00:1a:2b:3c:4d:5e}: Linux, macOS and most documentation (six groups of two). */
  COLON(':', 2),
  /** {@code 00-1a-2b-3c-4d-5e}: Windows {@code ipconfig /all} (six groups of two). */
  HYPHEN('-', 2),
  /** {@code 001a.2b3c.4d5e}: Cisco IOS (three groups of four). */
  CISCO_DOT('.', 4);

  private final char separator;
  private final int groupSize;

  MacStyle(char separator, int groupSize) {
    this.separator = separator;
    this.groupSize = groupSize;
  }

  /** The character between groups. */
  public char separator() {
    return separator;
  }

  /** The number of hex digits in each group. */
  public int groupSize() {
    return groupSize;
  }

  /** The number of groups in an address of this style. */
  int groupCount() {
    return 12 / groupSize;
  }
}
