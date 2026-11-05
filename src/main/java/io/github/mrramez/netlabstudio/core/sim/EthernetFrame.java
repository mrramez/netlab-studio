package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.Objects;

/**
 * An Ethernet II frame (IEEE 802.3), without preamble and FCS.
 *
 * @param srcMac the sender's MAC address; always a unicast address
 * @param dstMac the receiver's MAC address, or {@link MacAddress#BROADCAST}
 * @param etherType which protocol the payload belongs to; must match the payload's type
 * @param payload an {@link ArpMessage} or an {@link Ipv4Packet}
 */
public record EthernetFrame(MacAddress srcMac, MacAddress dstMac, EtherType etherType, Pdu payload)
    implements Pdu {

  /** Checks that the addresses are sensible and that the EtherType matches the payload. */
  public EthernetFrame {
    Objects.requireNonNull(srcMac, "srcMac");
    Objects.requireNonNull(dstMac, "dstMac");
    Objects.requireNonNull(etherType, "etherType");
    Objects.requireNonNull(payload, "payload");
    if (srcMac.isMulticast()) {
      throw new IllegalArgumentException(
          "a frame's source address must be a unicast address, but was " + srcMac);
    }
    boolean matches =
        switch (etherType) {
          case IPV4 -> payload instanceof Ipv4Packet;
          case ARP -> payload instanceof ArpMessage;
        };
    if (!matches) {
      throw new IllegalArgumentException(
          "EtherType " + etherType + " does not match a " + payload.getClass().getSimpleName());
    }
  }

  /** A frame carrying an ARP message. */
  public static EthernetFrame arp(MacAddress srcMac, MacAddress dstMac, ArpMessage message) {
    return new EthernetFrame(srcMac, dstMac, EtherType.ARP, message);
  }

  /** A frame carrying an IPv4 packet. */
  public static EthernetFrame ipv4(MacAddress srcMac, MacAddress dstMac, Ipv4Packet packet) {
    return new EthernetFrame(srcMac, dstMac, EtherType.IPV4, packet);
  }
}
