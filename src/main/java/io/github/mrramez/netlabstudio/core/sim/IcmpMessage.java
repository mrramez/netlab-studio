package io.github.mrramez.netlabstudio.core.sim;

import java.util.Objects;

/**
 * An ICMP message (RFC 792).
 *
 * <p>For an error message ({@link IcmpType#isError()}) real ICMP quotes the header of the packet
 * that caused it. The simulator keeps only what ping needs to match the error to its request: it
 * copies the identifier and sequence number of the original echo into {@code id} and {@code seq}.
 *
 * @param type the message type
 * @param code the code within the type, 0 to 255
 * @param id the identifier of the ping session, 0 to 65535
 * @param seq the sequence number of the echo, 0 to 65535
 */
public record IcmpMessage(IcmpType type, int code, int id, int seq) implements Pdu {

  private static final int MAX_16_BIT = 0xFFFF;

  /** Checks that the numbers fit their fields. */
  public IcmpMessage {
    Objects.requireNonNull(type, "type");
    if (code < 0 || code > 255) {
      throw new IllegalArgumentException("ICMP code must be between 0 and 255, but was " + code);
    }
    if (id < 0 || id > MAX_16_BIT || seq < 0 || seq > MAX_16_BIT) {
      throw new IllegalArgumentException(
          "ICMP id and sequence number must be between 0 and 65535, but were "
              + id
              + " and "
              + seq);
    }
  }
}
