package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The tables a device builds while traffic flows. They live in the engine, not in the model, so the
 * topology stays pure configuration, and they survive from one {@code ping} to the next (the second
 * ping needs no ARP). Entries never age: there is no wall clock, so a student clears a table
 * explicitly ({@code arp -d}, {@code clear mac address-table dynamic}).
 */
final class DeviceState {

  /** ARP cache of a host or router, in the order entries were learned. */
  final Map<Ipv4Address, ArpEntry> arp = new LinkedHashMap<>();

  /** MAC address table of a switch or bridge: source MAC to the port it was learned on. */
  final Map<MacAddress, String> macTable = new LinkedHashMap<>();
}
