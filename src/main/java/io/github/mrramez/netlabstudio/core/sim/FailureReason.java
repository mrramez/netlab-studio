package io.github.mrramez.netlabstudio.core.sim;

/** Why a ping did not get an answer. The events of the run explain the details. */
public enum FailureReason {
  /** The source has no IP address. */
  NO_IP_ADDRESS,
  /** The destination is on another network and the source has no default gateway. */
  NO_DEFAULT_GATEWAY,
  /** The default gateway is not on the source's own network, so it cannot be reached. */
  GATEWAY_NOT_ON_LOCAL_SUBNET,
  /** No cable is plugged into the port that has to send. */
  NOT_CONNECTED,
  /** The cable type cannot work between the two ports (strict cabling): the link is down. */
  WRONG_CABLE,
  /** A port or router interface on the path is shut down or has no working link. */
  INTERFACE_DOWN,
  /** A router has no route to the destination network. */
  NO_ROUTE,
  /** The TTL ran out on the way. */
  TTL_EXPIRED,
  /**
   * A router dropped the packet that triggered its ARP request (Cisco IOS behaviour), so this ping
   * timed out although the path works; the next one succeeds.
   */
  LOST_TO_ARP,
  /** Nobody answered the ARP request: the next hop is not there. */
  HOST_UNREACHABLE,
  /** The packet was sent but no answer arrived and nothing explains why. */
  NO_REPLY,
  /** The simulation was stopped because it produced too many events (a loop). */
  EVENT_LIMIT_EXCEEDED
}
