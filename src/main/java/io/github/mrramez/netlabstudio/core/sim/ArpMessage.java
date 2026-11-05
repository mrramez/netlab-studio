package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.Objects;

/**
 * An ARP message for IPv4 over Ethernet (RFC 826).
 *
 * @param op request or reply
 * @param senderMac the MAC address of the sender
 * @param senderIp the IP address of the sender
 * @param targetMac the MAC address being asked about ({@link #UNKNOWN_MAC} in a request) or the
 *     requester's MAC address (in a reply)
 * @param targetIp the IP address being asked about, or the requester's IP address in a reply
 */
public record ArpMessage(
    ArpOperation op,
    MacAddress senderMac,
    Ipv4Address senderIp,
    MacAddress targetMac,
    Ipv4Address targetIp)
    implements Pdu {

  /** {@code 00:00:00:00:00:00}: the "don't know yet" value in the target field of a request. */
  public static final MacAddress UNKNOWN_MAC = new MacAddress(0);

  /** Rejects missing fields. */
  public ArpMessage {
    Objects.requireNonNull(op, "op");
    Objects.requireNonNull(senderMac, "senderMac");
    Objects.requireNonNull(senderIp, "senderIp");
    Objects.requireNonNull(targetMac, "targetMac");
    Objects.requireNonNull(targetIp, "targetIp");
  }

  /** "Who has {@code targetIp}? Tell {@code senderIp}." */
  public static ArpMessage request(
      MacAddress senderMac, Ipv4Address senderIp, Ipv4Address targetIp) {
    return new ArpMessage(ArpOperation.REQUEST, senderMac, senderIp, UNKNOWN_MAC, targetIp);
  }

  /** "{@code senderIp} is at {@code senderMac}", sent back to the requester. */
  public static ArpMessage reply(
      MacAddress senderMac,
      Ipv4Address senderIp,
      MacAddress requesterMac,
      Ipv4Address requesterIp) {
    return new ArpMessage(ArpOperation.REPLY, senderMac, senderIp, requesterMac, requesterIp);
  }
}
