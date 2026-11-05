package io.github.mrramez.netlabstudio.core.sim;

/**
 * A protocol data unit: one of the things that travel through the simulated network. All PDUs are
 * immutable records, so an event can keep a snapshot of the exact bits at that moment even though
 * the next hop rewrites them.
 *
 * <p>An {@link EthernetFrame} carries an {@link ArpMessage} or an {@link Ipv4Packet}; an {@link
 * Ipv4Packet} carries an {@link IcmpMessage}.
 */
public sealed interface Pdu permits EthernetFrame, ArpMessage, Ipv4Packet, IcmpMessage {}
