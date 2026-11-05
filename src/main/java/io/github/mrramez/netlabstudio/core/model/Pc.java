package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.MacGenerator;

/** A personal computer. */
public final class Pc extends Host {

  Pc(String id, String name, MacGenerator macs) {
    super(id, name, DeviceType.PC, macs);
  }
}
