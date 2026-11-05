package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.model.Device;
import io.github.mrramez.netlabstudio.core.model.Port;

/**
 * What a hub or repeater does: it is a layer-1 device, so it knows nothing about frames or
 * addresses. Whatever arrives on one port is regenerated on every other port that has a working
 * cable.
 */
final class RepeaterBehavior {

  private RepeaterBehavior() {}

  static void receive(Simulation sim, Device device, Port in, EthernetFrame frame) {
    sim.emit(device, in, 1, SimAction.RECEIVE, frame, ExplanationKey.L1_SIGNAL_RECEIVED, in.name());
    for (Port out : device.ports()) {
      if (out != in && sim.topology().livePeer(out).isPresent()) {
        sim.transmit(
            out, frame, SimAction.REPEAT, 1, ExplanationKey.HUB_REPEAT, in.name(), out.name());
      }
    }
  }
}
