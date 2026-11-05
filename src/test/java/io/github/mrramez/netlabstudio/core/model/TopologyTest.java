package io.github.mrramez.netlabstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TopologyTest {

  private final Topology topology = new Topology();

  // ---- devices and their default ports ------------------------------------------------------

  @Test
  void typedFactoriesCreateTheRightDevices() {
    assertThat(topology.addPc("PC0"))
        .isInstanceOf(Pc.class)
        .extracting(Device::type)
        .isEqualTo(DeviceType.PC);
    assertThat(topology.addServer("S0").type()).isEqualTo(DeviceType.SERVER);
    assertThat(topology.addHub("H0").type()).isEqualTo(DeviceType.HUB);
    assertThat(topology.addRepeater("Rp0").type()).isEqualTo(DeviceType.REPEATER);
    assertThat(topology.addBridge("B0").type()).isEqualTo(DeviceType.BRIDGE);
    assertThat(topology.addSwitch("SW0").type()).isEqualTo(DeviceType.SWITCH);
    assertThat(topology.addRouter("R0").type()).isEqualTo(DeviceType.ROUTER);
    assertThat(topology.devices()).hasSize(7);
  }

  @Test
  void aHostHasANicAndASerialPort() {
    Pc pc = topology.addPc("PC0");

    assertThat(pc.ports()).extracting(Port::name).containsExactly("Fa0", "RS232");
    assertThat(pc.nic().type()).isEqualTo(PortType.FAST_ETHERNET);
    assertThat(pc.nic().mac()).isPresent();
    assertThat(pc.nic().isAdminUp()).isTrue();
    assertThat(pc.port("RS232").mac()).isEmpty();
  }

  @Test
  void hubAndRepeaterPortsHaveNoMacAddress() {
    Hub hub = topology.addHub("H0");
    Repeater repeater = topology.addRepeater("Rp0");

    assertThat(hub.ports()).hasSize(Hub.DEFAULT_PORT_COUNT);
    assertThat(hub.ports()).extracting(Port::name).startsWith("Port1", "Port2").endsWith("Port8");
    assertThat(hub.ports()).allSatisfy(port -> assertThat(port.mac()).isEmpty());
    assertThat(repeater.ports()).extracting(Port::name).containsExactly("Port1", "Port2");
  }

  @Test
  void aSwitchHas24FastEthernetPorts2GigabitPortsAndAConsole() {
    Switch sw = topology.addSwitch("SW0");

    assertThat(sw.ports()).hasSize(24 + 2 + 1);
    assertThat(sw.port("Fa0/1").type()).isEqualTo(PortType.FAST_ETHERNET);
    assertThat(sw.port("Fa0/24").isAdminUp()).isTrue();
    assertThat(sw.port("G0/2").type()).isEqualTo(PortType.GIGABIT_ETHERNET);
    assertThat(sw.port("Console").type()).isEqualTo(PortType.CONSOLE);
    assertThat(sw.port("Fa0/1").mac()).isPresent();
    assertThat(sw.findPort("Fa0/25")).isEmpty();
  }

  @Test
  void aBridgeHasFourPortsWithMacAddresses() {
    Bridge bridge = topology.addBridge("B0");

    assertThat(bridge.ports()).hasSize(Bridge.DEFAULT_PORT_COUNT);
    assertThat(bridge.ports()).allSatisfy(port -> assertThat(port.mac()).isPresent());
  }

  @Test
  void routerInterfacesStartAdministrativelyDown() {
    Router router = topology.addRouter("R0");

    assertThat(router.ports()).extracting(Port::name).containsExactly("G0/0", "G0/1", "Console");
    assertThat(router.port("G0/0").isAdminUp()).isFalse();
    assertThat(router.port("G0/1").isAdminUp()).isFalse();
    assertThat(router.port("Console").isAdminUp()).isTrue();
    // Every interface has its own MAC address: the simulator relies on this to show the rewrite.
    assertThat(router.port("G0/0").mac()).isNotEqualTo(router.port("G0/1").mac());
  }

  @Test
  void aRouterCanHaveSerialInterfaces() {
    Router router = topology.addRouter("R0", 1, 2);

    assertThat(router.ports())
        .extracting(Port::name)
        .containsExactly("G0/0", "S0/0/0", "S0/0/1", "Console");
    assertThat(router.port("S0/0/0").mac()).isEmpty();
    assertThat(router.port("S0/0/0").isAdminUp()).isFalse();
    assertThat(router.port("S0/0/0").canHaveIpv4()).isTrue();
  }

  @Test
  void rejectsARouterWithAnImpossibleInterfaceCount() {
    assertThatThrownBy(() -> topology.addRouter("R", 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> topology.addRouter("R", 9, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> topology.addRouter("R", 1, 5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> topology.addRouter("R", 1, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- ids, names, lookups -------------------------------------------------------------------

  @Test
  void generatedIdsAreUniqueAndSkipExplicitOnes() {
    topology.addDevice(DeviceType.PC, "d2", "Explicit");
    Device first = topology.addPc("A");
    Device second = topology.addPc("B");
    Device third = topology.addPc("C");

    assertThat(List.of(first.id(), second.id(), third.id()))
        .doesNotHaveDuplicates()
        .doesNotContain("d2");
  }

  @Test
  void devicesAreListedInTheOrderTheyWereAddedAndLookedUpById() {
    Device a = topology.addPc("A");
    Device b = topology.addSwitch("B");

    assertThat(topology.devices()).containsExactly(a, b);
    assertThat(topology.device(b.id())).isSameAs(b);
    assertThat(topology.findDevice("nope")).isEmpty();
    assertThatThrownBy(() -> topology.device("nope")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void devicesListIsASnapshot() {
    topology.addPc("A");
    List<Device> snapshot = topology.devices();
    topology.addPc("B");

    assertThat(snapshot).hasSize(1);
    assertThatThrownBy(() -> snapshot.add(topology.addPc("C")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsDuplicateAndInvalidIds() {
    topology.addDevice(DeviceType.PC, "pc-1", "A");

    assertThatThrownBy(() -> topology.addDevice(DeviceType.PC, "pc-1", "B"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pc-1");
    assertThatThrownBy(() -> topology.addDevice(DeviceType.PC, "has space", "B"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> topology.addDevice(DeviceType.PC, "", "B"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> topology.addDevice(DeviceType.PC, "x".repeat(65), "B"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> topology.addDevice(null, "a", "B"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> topology.addDevice(DeviceType.PC, (String) null, "B"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void aTopologyHoldsAtMostMaxDevices() {
    for (int i = 0; i < Topology.MAX_DEVICES; i++) {
      topology.addPc("PC" + i);
    }

    assertThatThrownBy(() -> topology.addPc("one too many"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> topology.addRouter("one too many", 1, 0))
        .isInstanceOf(IllegalStateException.class);
  }

  // ---- MAC addresses -------------------------------------------------------------------------

  @Test
  void theSameSeedGivesTheSameMacAddresses() {
    List<MacAddress> first = macsOf(new Topology(7));
    List<MacAddress> again = macsOf(new Topology(7));
    List<MacAddress> other = macsOf(new Topology(8));

    assertThat(again).isEqualTo(first);
    assertThat(other).isNotEqualTo(first);
  }

  private static List<MacAddress> macsOf(Topology topology) {
    topology.addPc("A");
    topology.addSwitch("B");
    topology.addRouter("C");
    List<MacAddress> macs = new ArrayList<>();
    topology.devices().forEach(d -> d.ports().forEach(p -> p.mac().ifPresent(macs::add)));
    return macs;
  }

  @Test
  void everyMacIsUniqueUnicastAndLocallyAdministered() {
    List<MacAddress> macs = macsOf(topology);

    assertThat(new HashSet<>(macs)).hasSameSizeAs(macs);
    assertThat(macs)
        .allSatisfy(
            mac -> {
              assertThat(mac.isUnicast()).isTrue();
              assertThat(mac.isLocallyAdministered()).isTrue();
            });
  }

  // ---- links ---------------------------------------------------------------------------------

  @Test
  void connectsTwoFreePortsAndRecordsTheCable() {
    Pc pc = topology.addPc("PC");
    Switch sw = topology.addSwitch("SW");

    Link link = topology.connect(pc, "Fa0", sw, "Fa0/1", CableType.STRAIGHT);

    assertThat(topology.links()).containsExactly(link);
    assertThat(link.cable()).isEqualTo(CableType.STRAIGHT);
    assertThat(link.other(pc.nic())).isSameAs(sw.port("Fa0/1"));
    assertThat(link.other(sw.port("Fa0/1"))).isSameAs(pc.nic());
    assertThat(pc.nic().link()).contains(link);
    assertThat(pc.nic().isConnected()).isTrue();
    assertThat(link.isUp()).isTrue();
    assertThat(link.toString()).contains(pc.nic().id()).contains("STRAIGHT");
    assertThatThrownBy(() -> link.other(sw.port("Fa0/2")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aWrongCableIsAcceptedButTheLinkStaysDown() {
    Pc a = topology.addPc("A");
    Pc b = topology.addPc("B");

    Link link = topology.connect(a.nic(), b.nic(), CableType.STRAIGHT);

    assertThat(link.state()).isEqualTo(LinkState.DOWN);
    assertThat(a.nic().linkState()).isEqualTo(LinkState.DOWN);
    assertThat(a.nic().isLinkUp()).isFalse();
  }

  @Test
  void switchingCableRulesChangesEveryLinkAtOnce() {
    Pc a = topology.addPc("A");
    Pc b = topology.addPc("B");
    Link link = topology.connect(a.nic(), b.nic(), CableType.STRAIGHT);

    topology.setCableRules(CableRules.autoMdix());
    assertThat(link.state()).isEqualTo(LinkState.UP);
    topology.setCableRules(CableRules.strictCabling());
    assertThat(link.state()).isEqualTo(LinkState.DOWN);
    assertThat(topology.cableRules()).isEqualTo(CableRules.strictCabling());
  }

  @Test
  void rejectsBadConnections() {
    Pc a = topology.addPc("A");
    Pc b = topology.addPc("B");
    Pc c = topology.addPc("C");
    topology.connect(a.nic(), b.nic(), CableType.CROSSOVER);
    Topology elsewhere = new Topology();
    Pc stranger = elsewhere.addPc("Stranger");

    assertThatThrownBy(() -> topology.connect(a.nic(), c.nic(), CableType.CROSSOVER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already has a cable");
    assertThatThrownBy(() -> topology.connect(c.nic(), b.nic(), CableType.CROSSOVER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> topology.connect(c.nic(), c.nic(), CableType.CROSSOVER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> topology.connect(c.nic(), stranger.nic(), CableType.CROSSOVER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not part of this topology");
    assertThatThrownBy(() -> topology.connect(c.nic(), null, CableType.CROSSOVER))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> topology.connect(c.nic(), a.port("RS232"), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void aCableBetweenTwoPortsOfTheSameDeviceIsAllowed() {
    // A loop cable on one switch is a classic mistake that the simulator should be able to show.
    Switch sw = topology.addSwitch("SW");

    Link link = topology.connect(sw, "Fa0/1", sw, "Fa0/2", CableType.CROSSOVER);

    assertThat(link.isUp()).isTrue();
  }

  @Test
  void aTopologyHoldsAtMostMaxLinks() {
    List<Port> free = new ArrayList<>();
    for (int i = 0; i < 170; i++) {
      for (Port port : topology.addSwitch("SW" + i).ports()) {
        if (port.type().isEthernet()) {
          free.add(port);
        }
      }
    }
    for (int i = 0; i < Topology.MAX_LINKS; i++) {
      topology.connect(free.get(2 * i), free.get(2 * i + 1), CableType.CROSSOVER);
    }

    assertThat(topology.links()).hasSize(Topology.MAX_LINKS);
    Port a = free.get(2 * Topology.MAX_LINKS);
    Port b = free.get(2 * Topology.MAX_LINKS + 1);
    assertThatThrownBy(() -> topology.connect(a, b, CableType.CROSSOVER))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void disconnectFreesBothPorts() {
    Pc a = topology.addPc("A");
    Pc b = topology.addPc("B");
    Link link = topology.connect(a.nic(), b.nic(), CableType.CROSSOVER);

    topology.disconnect(link);

    assertThat(topology.links()).isEmpty();
    assertThat(a.nic().isConnected()).isFalse();
    assertThat(b.nic().link()).isEmpty();
    assertThat(link.state()).isEqualTo(LinkState.DOWN);
    assertThat(link.isUp()).isFalse();
    assertThatThrownBy(() -> topology.disconnect(link))
        .isInstanceOf(IllegalArgumentException.class);
    // The ports can be reused.
    assertThat(topology.connect(a.nic(), b.nic(), CableType.CROSSOVER).isUp()).isTrue();
  }

  @Test
  void removingADeviceRemovesItsLinks() {
    Pc a = topology.addPc("A");
    Switch sw = topology.addSwitch("SW");
    Pc b = topology.addPc("B");
    topology.connect(a.nic(), sw.port("Fa0/1"), CableType.STRAIGHT);
    topology.connect(b.nic(), sw.port("Fa0/2"), CableType.STRAIGHT);

    topology.removeDevice(sw.id());

    assertThat(topology.links()).isEmpty();
    assertThat(a.nic().isConnected()).isFalse();
    assertThat(topology.devices()).containsExactly(a, b);
    assertThatThrownBy(() -> topology.removeDevice(sw.id()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> topology.diagnose(sw.port("Fa0/1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void linkIdsAreSequential() {
    Pc a = topology.addPc("A");
    Pc b = topology.addPc("B");
    Pc c = topology.addPc("C");
    Pc d = topology.addPc("D");

    Set<String> ids = new HashSet<>();
    ids.add(topology.connect(a.nic(), b.nic(), CableType.CROSSOVER).id());
    ids.add(topology.connect(c.nic(), d.nic(), CableType.CROSSOVER).id());

    assertThat(ids).containsExactlyInAnyOrder("L1", "L2");
  }

  @Test
  void livePeerIsOnlyPresentWhenTheLinkCanCarryFrames() {
    Pc pc = topology.addPc("PC");
    Switch sw = topology.addSwitch("SW");
    Router router = topology.addRouter("R");
    Link ok = topology.connect(pc.nic(), sw.port("Fa0/1"), CableType.STRAIGHT);
    Link console = topology.connect(pc.port("RS232"), router.port("Console"), CableType.CONSOLE);

    assertThat(topology.livePeer(pc.nic())).contains(sw.port("Fa0/1"));
    assertThat(topology.livePeer(sw.port("Fa0/1"))).contains(pc.nic());
    assertThat(ok.carriesFrames()).isTrue();
    // A console cable is up but carries no network traffic.
    assertThat(console.isUp()).isTrue();
    assertThat(console.carriesFrames()).isFalse();
    assertThat(topology.livePeer(pc.port("RS232"))).isEmpty();
    // No cable at all.
    assertThat(topology.livePeer(sw.port("Fa0/2"))).isEmpty();
    // Shut down.
    sw.port("Fa0/1").setAdminUp(false);
    assertThat(topology.livePeer(pc.nic())).isEmpty();
  }

  @Test
  void aSerialLinkIsUpButCarriesNoFramesYet() {
    Router first = topology.addRouter("R1", 1, 1);
    Router second = topology.addRouter("R2", 1, 1);
    first.port("S0/0/0").setAdminUp(true);
    second.port("S0/0/0").setAdminUp(true);

    Link serial = topology.connect(first.port("S0/0/0"), second.port("S0/0/0"), CableType.SERIAL);

    assertThat(serial.isUp()).isTrue();
    assertThat(serial.carriesFrames()).isFalse();
  }
}
