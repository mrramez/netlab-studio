package io.github.mrramez.netlabstudio.core.sim;

/** The operation field of an ARP message (RFC 826). */
public enum ArpOperation {
  /** Operation 1: "who has this IP address? tell me your MAC address". Sent as a broadcast. */
  REQUEST(1),
  /** Operation 2: "that IP address is at this MAC address". Sent as a unicast. */
  REPLY(2);

  private final int value;

  ArpOperation(int value) {
    this.value = value;
  }

  /** The value on the wire. */
  public int value() {
    return value;
  }
}
