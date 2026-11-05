package io.github.mrramez.netlabstudio.core.sim;

/** The EtherType field of an Ethernet frame: which protocol the payload belongs to. */
public enum EtherType {
  /** {@code 0x0800}: the payload is an IPv4 packet. */
  IPV4(0x0800),
  /** {@code 0x0806}: the payload is an ARP message. */
  ARP(0x0806);

  private final int value;

  EtherType(int value) {
    this.value = value;
  }

  /** The 16-bit value on the wire. */
  public int value() {
    return value;
  }
}
