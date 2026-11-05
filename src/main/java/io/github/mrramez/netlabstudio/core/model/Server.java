package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.MacGenerator;

/** A server. In the simulator it behaves like a {@link Pc}; services come in later phases. */
public final class Server extends Host {

  Server(String id, String name, MacGenerator macs) {
    super(id, name, DeviceType.SERVER, macs);
  }
}
