package io.github.mrramez.netlabstudio.core.sim;

/**
 * What a device did in one simulation step. The UI picks an icon and an animation from this; the
 * explanation key says why.
 */
public enum SimAction {
  /** A device created a message: a ping, an ARP request or reply, an echo reply, an ICMP error. */
  GENERATE,
  /** Headers were added: an IP packet was put into a frame. */
  ENCAPSULATE,
  /** The frame was accepted by the NIC and its header removed. */
  DECAPSULATE,
  /** A decision was taken from addresses: local or remote? */
  DECIDE,
  /** A table was searched: the ARP cache or the routing table. */
  LOOKUP,
  /** An ARP cache entry was added. */
  CACHE_UPDATE,
  /** A switch or bridge learned (or refreshed) where a source MAC address lives. */
  LEARN,
  /**
   * A switch or bridge decided to send the frame out of every port except the one it came in on.
   */
  FLOOD,
  /** A switch or bridge decided to send the frame out of one particular port. */
  FORWARD,
  /** A bridge decided not to forward the frame because the destination is on the same segment. */
  FILTER,
  /** A router replaced the frame's source and destination MAC addresses for the next hop. */
  REWRITE,
  /** A router subtracted one from the TTL. */
  DECREMENT_TTL,
  /** A packet was held until ARP finds the next hop's MAC address. */
  QUEUE,
  /** A held packet is sent now that ARP has found the next hop's MAC address. */
  RELEASE,
  /** A device put a frame on the cable (layer 2). */
  TRANSMIT,
  /** A hub or repeater regenerated the signal on a port (layer 1). */
  REPEAT,
  /** A frame (or signal) arrived on a port. */
  RECEIVE,
  /**
   * A message reached the device it was meant for and was handed to the protocol that handles it.
   */
  DELIVER,
  /** A frame or packet was thrown away. The explanation says why. */
  DROP,
  /** Waiting ended without an answer. */
  TIMEOUT;

  /** Whether this action puts a frame on a cable ({@link #TRANSMIT} or {@link #REPEAT}). */
  public boolean isTransmission() {
    return this == TRANSMIT || this == REPEAT;
  }
}
