package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.model.CableType;
import io.github.mrramez.netlabstudio.core.model.Device;
import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Link;
import io.github.mrramez.netlabstudio.core.model.Port;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.model.Topology;
import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import io.github.mrramez.netlabstudio.core.net.SubnetMask;
import java.util.List;
import java.util.stream.Collectors;

/** Test helpers that build small topologies and print events compactly. */
final class Lab {

  private Lab() {}

  static Ipv4Address ip(String text) {
    return Ipv4Address.parse(text);
  }

  static SubnetMask mask(String text) {
    return SubnetMask.parse(text);
  }

  /** A PC with a /24 address and, optionally, a default gateway. */
  static Host pc(Topology topology, String name, String address, String gateway) {
    Host host = topology.addPc(name);
    if (gateway == null) {
      host.configure(ip(address), mask("/24"));
    } else {
      host.configure(ip(address), mask("/24"), ip(gateway));
    }
    return host;
  }

  /** Plugs the cable that the pin-outs call for, so the link comes up in strict mode. */
  static Link cable(Topology topology, Device a, String portA, Device b, String portB) {
    Port first = a.port(portA);
    Port second = b.port(portB);
    CableType cable = topology.cableRules().recommendedCable(first, second).orElseThrow();
    return topology.connect(first, second, cable);
  }

  /** Gives a router interface an address and switches it on. */
  static void configure(Router router, String port, String address, String mask) {
    router.port(port).setIpv4(ip(address), mask(mask));
    router.port(port).setAdminUp(true);
  }

  static MacAddress mac(Device device, String port) {
    return device.port(port).mac().orElseThrow();
  }

  /** One line per event: {@code t=2 PC0.Fa0 L2 RECEIVE sim.l2.frameReceived}. */
  static String line(Topology topology, SimEvent event) {
    String device = topology.device(event.deviceId()).name();
    String where = event.hasPort() ? device + "." + event.portName() : device;
    return "t="
        + event.time()
        + " "
        + where
        + " L"
        + event.layer()
        + " "
        + event.action()
        + " "
        + event.explanationKey();
  }

  static List<String> lines(Topology topology, List<SimEvent> events) {
    return events.stream().map(event -> line(topology, event)).collect(Collectors.toList());
  }

  /** A full dump including arguments and PDU, for debugging and for reading a trace. */
  static String dump(Topology topology, List<SimEvent> events) {
    StringBuilder text = new StringBuilder();
    for (SimEvent event : events) {
      text.append(line(topology, event)).append(' ').append(event.args());
      if (event.pdu() != null) {
        text.append("\n      ").append(event.pdu());
      }
      text.append('\n');
    }
    return text.toString();
  }
}
