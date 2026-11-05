package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import java.util.Objects;

/**
 * The fields of an IPv4 packet (RFC 791) that the simulator shows and acts on. Version, header
 * length, checksum and the like are not modelled.
 *
 * @param src the source address; stays the same from sender to receiver
 * @param dst the destination address; stays the same from sender to receiver
 * @param ttl time to live, 0 to 255; every router subtracts one and drops the packet at zero
 * @param protocol which protocol the payload belongs to; must match the payload's type
 * @param payload an {@link IcmpMessage}
 */
public record Ipv4Packet(
    Ipv4Address src, Ipv4Address dst, int ttl, IpProtocol protocol, Pdu payload) implements Pdu {

  /** Checks the TTL range and that the protocol matches the payload. */
  public Ipv4Packet {
    Objects.requireNonNull(src, "src");
    Objects.requireNonNull(dst, "dst");
    Objects.requireNonNull(protocol, "protocol");
    Objects.requireNonNull(payload, "payload");
    if (ttl < 0 || ttl > 255) {
      throw new IllegalArgumentException("TTL must be between 0 and 255, but was " + ttl);
    }
    if (protocol == IpProtocol.ICMP && !(payload instanceof IcmpMessage)) {
      throw new IllegalArgumentException(
          "protocol ICMP does not match a " + payload.getClass().getSimpleName());
    }
  }

  /** An ICMP message in a packet. */
  public static Ipv4Packet icmp(Ipv4Address src, Ipv4Address dst, int ttl, IcmpMessage message) {
    return new Ipv4Packet(src, dst, ttl, IpProtocol.ICMP, message);
  }

  /** The same packet with another TTL. */
  public Ipv4Packet withTtl(int newTtl) {
    return new Ipv4Packet(src, dst, newTtl, protocol, payload);
  }
}
