package io.github.mrramez.netlabstudio.core.sim;

/** The ICMP message types (RFC 792) the simulator uses. */
public enum IcmpType {
  /** Type 0: the answer to an echo request. */
  ECHO_REPLY(0),
  /**
   * Type 3: the packet could not be delivered. Code 0 means "network unreachable" (no route), code
   * 1 "host unreachable" (the router could not find the host on the last hop).
   */
  DESTINATION_UNREACHABLE(3),
  /** Type 8: "are you there?", the message {@code ping} sends. */
  ECHO_REQUEST(8),
  /** Type 11, code 0: a router dropped the packet because its TTL ran out. */
  TIME_EXCEEDED(11);

  private final int number;

  IcmpType(int number) {
    this.number = number;
  }

  /** The type number on the wire. */
  public int number() {
    return number;
  }

  /** Whether this is an error report rather than an echo. */
  public boolean isError() {
    return this == DESTINATION_UNREACHABLE || this == TIME_EXCEEDED;
  }
}
