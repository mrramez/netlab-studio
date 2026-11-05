package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.model.Device;
import io.github.mrramez.netlabstudio.core.model.Port;
import io.github.mrramez.netlabstudio.core.net.MacAddress;

/**
 * What a bridge or switch does: learn where each source MAC address lives, then forward a frame to
 * the one port that leads to its destination, filter it if the destination is on the port it came
 * from, or flood it out of every other port when the destination is a broadcast, a multicast or not
 * known yet.
 */
final class BridgeBehavior {

  private BridgeBehavior() {}

  static void receive(Simulation sim, Device device, Port in, EthernetFrame frame) {
    sim.emit(
        device,
        in,
        2,
        SimAction.RECEIVE,
        frame,
        ExplanationKey.L2_FRAME_RECEIVED,
        in.name(),
        frame.srcMac(),
        frame.dstMac());
    DeviceState state = sim.state(device);

    MacAddress source = frame.srcMac();
    String learnedOn = state.macTable.put(source, in.name());
    if (learnedOn == null) {
      sim.emit(
          device,
          in,
          2,
          SimAction.LEARN,
          frame,
          ExplanationKey.SWITCH_LEARN_NEW,
          source,
          in.name());
    } else if (learnedOn.equals(in.name())) {
      sim.emit(
          device,
          in,
          2,
          SimAction.LEARN,
          frame,
          ExplanationKey.SWITCH_LEARN_REFRESH,
          source,
          in.name());
    } else {
      sim.emit(
          device,
          in,
          2,
          SimAction.LEARN,
          frame,
          ExplanationKey.SWITCH_LEARN_MOVED,
          source,
          learnedOn,
          in.name());
    }

    MacAddress destination = frame.dstMac();
    // Group addresses are never in the table: they are never a source address.
    String knownPort = destination.isMulticast() ? null : state.macTable.get(destination);
    if (knownPort == null) {
      ExplanationKey why =
          destination.isMulticast()
              ? ExplanationKey.SWITCH_FLOOD_BROADCAST
              : ExplanationKey.SWITCH_FLOOD_UNKNOWN;
      sim.emit(device, in, 2, SimAction.FLOOD, frame, why, destination, in.name());
      for (Port out : device.ports()) {
        if (out != in && sim.topology().livePeer(out).isPresent()) {
          sim.transmit(
              out,
              frame,
              SimAction.TRANSMIT,
              2,
              ExplanationKey.L2_FRAME_SENT,
              out.name(),
              destination);
        }
      }
    } else if (knownPort.equals(in.name())) {
      sim.emit(
          device,
          in,
          2,
          SimAction.FILTER,
          frame,
          ExplanationKey.SWITCH_FILTER,
          destination,
          in.name());
    } else {
      sim.emit(
          device,
          in,
          2,
          SimAction.FORWARD,
          frame,
          ExplanationKey.SWITCH_FORWARD,
          destination,
          knownPort);
      sim.transmit(
          device.port(knownPort),
          frame,
          SimAction.TRANSMIT,
          2,
          ExplanationKey.L2_FRAME_SENT,
          knownPort,
          destination);
    }
  }
}
