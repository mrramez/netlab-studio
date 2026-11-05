package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.MacGenerator;

/**
 * A repeater: a layer-1 device with two ports that regenerates the signal (it does not merely
 * amplify it) to extend a cable segment.
 */
public final class Repeater extends Device {

  Repeater(String id, String name, MacGenerator macs) {
    super(id, name, DeviceType.REPEATER, Hub.portSpecs(2), macs);
  }
}
