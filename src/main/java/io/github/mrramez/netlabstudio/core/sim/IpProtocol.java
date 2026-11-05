package io.github.mrramez.netlabstudio.core.sim;

/** The Protocol field of an IPv4 header: which protocol the payload belongs to. */
public enum IpProtocol {
  /** Protocol 1: ICMP (RFC 792). */
  ICMP(1);

  private final int value;

  IpProtocol(int value) {
    this.value = value;
  }

  /** The 8-bit value on the wire. */
  public int value() {
    return value;
  }
}
