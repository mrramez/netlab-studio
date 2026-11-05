package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mrramez.netlabstudio.core.model.Bridge;
import io.github.mrramez.netlabstudio.core.model.CableType;
import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Hub;
import io.github.mrramez.netlabstudio.core.model.Link;
import io.github.mrramez.netlabstudio.core.model.Switch;
import io.github.mrramez.netlabstudio.core.model.Topology;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MAC learning, filtering, flooding and the corner cases of bridges and switches. */
class SwitchLearningTest {

  private final Topology topology = new Topology();

  private static List<String> keys(List<SimEvent> events) {
    return events.stream().map(SimEvent::explanationKey).toList();
  }

  @Test
  void aBridgeFiltersAFrameWhoseDestinationIsOnTheSegmentItCameFrom() {
    Bridge bridge = topology.addBridge("BR");
    Hub left = topology.addHub("LEFT");
    Hub right = topology.addHub("RIGHT");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Host c = Lab.pc(topology, "C", "192.168.1.30", null);
    Lab.cable(topology, left, "Port1", bridge, "Port1");
    Lab.cable(topology, right, "Port1", bridge, "Port2");
    Lab.cable(topology, a, "Fa0", left, "Port2");
    Lab.cable(topology, b, "Fa0", left, "Port3");
    Lab.cable(topology, c, "Fa0", right, "Port2");
    SimulationEngine engine = new SimulationEngine(topology);

    PingResult result = engine.ping(a.id(), Lab.ip("192.168.1.20"), 2);

    // B's reply to A never leaves the left segment: the bridge learned A there and filters it.
    List<SimEvent> filtered =
        result.events().stream().filter(e -> e.action() == SimAction.FILTER).toList();
    assertThat(filtered).isNotEmpty();
    assertThat(filtered)
        .allSatisfy(
            e -> {
              assertThat(e.deviceId()).isEqualTo(bridge.id());
              assertThat(e.portName()).isEqualTo("Port1");
              assertThat(e.explanationKey()).isEqualTo("sim.switch.filterSameSegment");
            });
    // C only ever saw the broadcast ARP request, which a bridge floods.
    assertThat(result.events())
        .filteredOn(e -> e.deviceId().equals(c.id()) && e.action() == SimAction.RECEIVE)
        .hasSize(1);
    assertThat(engine.macTable(bridge.id()))
        .containsExactly(
            new MacEntry(Lab.mac(a, "Fa0"), "Port1"), new MacEntry(Lab.mac(b, "Fa0"), "Port1"));
  }

  @Test
  void aMacAddressThatMovesToAnotherPortIsRelearned() {
    Switch sw = topology.addSwitch("SW");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Link cable = Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    SimulationEngine engine = new SimulationEngine(topology);
    engine.ping(a.id(), Lab.ip("192.168.1.20"), 2);

    topology.disconnect(cable);
    Lab.cable(topology, a, "Fa0", sw, "Fa0/5");
    PingResult again = engine.ping(a.id(), Lab.ip("192.168.1.20"), 1);

    SimEvent moved =
        again.events().stream()
            .filter(e -> e.explanationKey().equals("sim.switch.learnMoved"))
            .findFirst()
            .orElseThrow();
    assertThat(moved.args()).containsExactly(Lab.mac(a, "Fa0").toString(), "Fa0/1", "Fa0/5");
    assertThat(engine.macTable(sw.id()))
        .containsExactlyInAnyOrder(
            new MacEntry(Lab.mac(a, "Fa0"), "Fa0/5"), new MacEntry(Lab.mac(b, "Fa0"), "Fa0/2"));
    assertThat(again.allReceived()).isTrue();
  }

  @Test
  void aClearedMacTableMakesTheSwitchFloodAgain() {
    Switch sw = topology.addSwitch("SW");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Host c = Lab.pc(topology, "C", "192.168.1.30", null);
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    Lab.cable(topology, c, "Fa0", sw, "Fa0/3");
    SimulationEngine engine = new SimulationEngine(topology);
    engine.ping(a.id(), Lab.ip("192.168.1.20"), 2);
    assertThat(engine.ping(a.id(), Lab.ip("192.168.1.20"), 1).events())
        .noneMatch(e -> e.explanationKey().equals("sim.switch.floodUnknownUnicast"));

    engine.clearMacTable(sw.id());
    PingResult after = engine.ping(a.id(), Lab.ip("192.168.1.20"), 1);

    // A's frame to B is now an unknown unicast: flooded, so C's NIC sees (and drops) it.
    assertThat(keys(after.events())).contains("sim.switch.floodUnknownUnicast");
    assertThat(after.events())
        .filteredOn(e -> e.deviceId().equals(c.id()) && e.action() == SimAction.DROP)
        .extracting(SimEvent::explanationKey)
        .contains("sim.nic.frameNotForMe");
    assertThat(after.allReceived()).isTrue();
  }

  @Test
  void aMulticastFrameIsFloodedLikeABroadcast() {
    Switch sw = topology.addSwitch("SW");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Host c = Lab.pc(topology, "C", "192.168.1.30", null);
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    Lab.cable(topology, c, "Fa0", sw, "Fa0/3");
    Simulation simulation = new SimulationEngine(topology).newSimulation();
    ArpMessage arp =
        ArpMessage.request(Lab.mac(a, "Fa0"), Lab.ip("192.168.1.10"), Lab.ip("192.168.1.20"));
    EthernetFrame frame =
        EthernetFrame.arp(Lab.mac(a, "Fa0"), MacAddress.parse("01:00:5e:00:00:01"), arp);

    List<SimEvent> events = simulation.inject(sw.port("Fa0/1"), frame);

    assertThat(keys(events)).contains("sim.switch.floodBroadcast");
    assertThat(events)
        .filteredOn(e -> e.deviceId().equals(sw.id()) && e.action() == SimAction.TRANSMIT)
        .extracting(SimEvent::portName)
        .containsExactly("Fa0/2", "Fa0/3");
    // NICs ignore multicast frames they have not subscribed to.
    assertThat(events)
        .filteredOn(e -> e.action() == SimAction.DROP)
        .extracting(SimEvent::explanationKey)
        .containsExactly("sim.nic.frameNotForMe", "sim.nic.frameNotForMe");
  }

  @Test
  void aKnownDestinationBehindADeadLinkIsDroppedWithAReason() {
    Switch sw = topology.addSwitch("SW");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    SimulationEngine engine = new SimulationEngine(topology);
    engine.ping(a.id(), Lab.ip("192.168.1.20"), 2);
    sw.port("Fa0/1").setAdminUp(false); // A's port goes down; the table still points at it
    Simulation simulation = engine.newSimulation();
    ArpMessage reply =
        ArpMessage.reply(
            Lab.mac(b, "Fa0"), Lab.ip("192.168.1.20"), Lab.mac(a, "Fa0"), Lab.ip("192.168.1.10"));
    EthernetFrame toA = EthernetFrame.arp(Lab.mac(b, "Fa0"), Lab.mac(a, "Fa0"), reply);

    List<SimEvent> events = simulation.inject(sw.port("Fa0/2"), toA);

    assertThat(keys(events))
        .containsExactly(
            "sim.l2.frameReceived",
            "sim.switch.learnKnown",
            "sim.switch.forwardKnown",
            "sim.l1.noCarrier");
    assertThat(events.get(3).args()).containsExactly("Fa0/1");
  }

  @Test
  void aSwitchWithNoOtherCablePlugsInFloodsToNobody() {
    Switch sw = topology.addSwitch("SW");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.20"), 1);

    assertThat(keys(result.events())).contains("sim.switch.floodBroadcast");
    assertThat(result.events())
        .filteredOn(e -> e.deviceId().equals(sw.id()) && e.action() == SimAction.TRANSMIT)
        .isEmpty();
    assertThat(result.attempts().get(0).reason()).contains(FailureReason.HOST_UNREACHABLE);
  }

  @Test
  void aRepeaterRegeneratesTheSignalOnItsOtherPortOnly() {
    var repeater = topology.addRepeater("REP");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Lab.cable(topology, a, "Fa0", repeater, "Port1");
    Lab.cable(topology, b, "Fa0", repeater, "Port2");

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.20"), 2);

    assertThat(result.allReceived()).isFalse();
    assertThat(result.attempts().get(1).success()).isTrue();
    assertThat(result.events())
        .filteredOn(e -> e.deviceId().equals(repeater.id()) && e.action() == SimAction.REPEAT)
        .extracting(SimEvent::portName)
        .containsExactly("Port2", "Port1", "Port2", "Port1");
  }

  @Test
  void aCrossoverCableOnTheUplinkBetweenTwoSwitchesWorksLikeAnyOther() {
    Switch first = topology.addSwitch("SW1");
    Switch second = topology.addSwitch("SW2");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    topology.connect(first.port("G0/1"), second.port("G0/1"), CableType.CROSSOVER);
    Lab.cable(topology, a, "Fa0", first, "Fa0/1");
    Lab.cable(topology, b, "Fa0", second, "Fa0/1");
    SimulationEngine engine = new SimulationEngine(topology);

    PingResult result = engine.ping(a.id(), Lab.ip("192.168.1.20"), 2);

    assertThat(result.attempts().get(1).success()).isTrue();
    // Each switch learned both stations: A and B each appear on the uplink of the far switch.
    assertThat(engine.macTable(first.id()))
        .containsExactly(
            new MacEntry(Lab.mac(a, "Fa0"), "Fa0/1"), new MacEntry(Lab.mac(b, "Fa0"), "G0/1"));
    assertThat(engine.macTable(second.id()))
        .containsExactly(
            new MacEntry(Lab.mac(a, "Fa0"), "G0/1"), new MacEntry(Lab.mac(b, "Fa0"), "Fa0/1"));
    assertThat(result.attempts().get(1).roundTripTicks()).isEqualTo(6);
  }
}
