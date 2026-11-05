package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.model.Switch;
import io.github.mrramez.netlabstudio.core.model.Topology;
import io.github.mrramez.netlabstudio.core.net.Ipv4Network;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The public API the UI uses: ping, tables, validation, determinism. */
class SimulationEngineApiTest {

  private final Topology topology = new Topology();

  private record Lan(Host a, Host b, Switch sw, SimulationEngine engine) {}

  private Lan lan() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Switch sw = topology.addSwitch("SW");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    return new Lan(a, b, sw, new SimulationEngine(topology));
  }

  // ---- validation ----------------------------------------------------------------------------

  @Test
  void onlyAPcOrServerCanPing() {
    Lan lan = lan();

    assertThatThrownBy(() -> lan.engine().ping(lan.sw().id(), Lab.ip("192.168.1.10"), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only a PC or server");
    assertThatThrownBy(() -> lan.engine().ping("nope", Lab.ip("192.168.1.10"), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> lan.engine().ping(lan.a().id(), null, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> lan.engine().ping(null, Lab.ip("192.168.1.10"), 1))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void theCountMustBeBetweenOneAndFifty() {
    Lan lan = lan();

    assertThatThrownBy(() -> lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 51))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            lan.engine()
                .ping(lan.a().id(), Lab.ip("192.168.1.20"), SimulationEngine.MAX_PING_COUNT)
                .sent())
        .isEqualTo(50);
  }

  @Test
  void theConstructorsRejectNull() {
    assertThatThrownBy(() -> new SimulationEngine(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new SimulationEngine(topology, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void exposesItsTopologyAndSettings() {
    SimulationSettings settings = SimulationSettings.defaults().withHostTtl(64);
    SimulationEngine engine = new SimulationEngine(topology, settings);

    assertThat(engine.topology()).isSameAs(topology);
    assertThat(engine.settings()).isEqualTo(settings);
    assertThat(new SimulationEngine(topology).settings()).isEqualTo(SimulationSettings.defaults());
  }

  // ---- tables --------------------------------------------------------------------------------

  @Test
  void tablesAreOnlyAvailableOnTheDevicesThatHaveThem() {
    Lan lan = lan();
    Router router = topology.addRouter("R");

    assertThatThrownBy(() -> lan.engine().macTable(lan.a().id()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> lan.engine().arpTable(lan.sw().id()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> lan.engine().routingTable(lan.a().id()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(lan.engine().arpTable(router.id())).isEmpty();
    assertThat(lan.engine().routingTable(router.id())).isEmpty();
    assertThat(lan.engine().macTable(lan.sw().id())).isEmpty();
    assertThatThrownBy(() -> lan.engine().macTable("nope"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aBridgeHasAMacTableToo() {
    var bridge = topology.addBridge("BR");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Lab.cable(topology, a, "Fa0", bridge, "Port1");
    SimulationEngine engine = new SimulationEngine(topology);

    engine.ping(a.id(), Lab.ip("192.168.1.20"), 1);

    assertThat(engine.macTable(bridge.id()))
        .containsExactly(new MacEntry(Lab.mac(a, "Fa0"), "Port1"));
  }

  @Test
  void theTablesPersistBetweenPingsAndCanBeCleared() {
    Lan lan = lan();
    lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 2);
    assertThat(lan.engine().arpTable(lan.a().id())).hasSize(1);
    assertThat(lan.engine().macTable(lan.sw().id())).hasSize(2);

    lan.engine().clearArpTable(lan.a().id());
    assertThat(lan.engine().arpTable(lan.a().id())).isEmpty();
    assertThat(lan.engine().arpTable(lan.b().id())).hasSize(1);
    // With the cache empty again, A has to ask again, but it holds its packet: no ping is lost.
    PingResult again = lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 1);
    assertThat(again.allReceived()).isTrue();
    assertThat(again.events())
        .extracting(SimEvent::explanationKey)
        .contains("sim.arp.cacheMiss", "sim.arp.requestSent");

    lan.engine().resetState();
    assertThat(lan.engine().arpTable(lan.b().id())).isEmpty();
    assertThat(lan.engine().macTable(lan.sw().id())).isEmpty();
  }

  @Test
  void theReturnedTablesAreSnapshots() {
    Lan lan = lan();
    lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 2);
    List<ArpEntry> snapshot = lan.engine().arpTable(lan.a().id());

    lan.engine().clearArpTable(lan.a().id());

    assertThat(snapshot).hasSize(1);
    assertThatThrownBy(() -> snapshot.clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void theRoutingTableOnlyListsInterfacesThatAreUpUp() {
    Router router = topology.addRouter("R1");
    Switch sw = topology.addSwitch("SW");
    Lab.configure(router, "G0/0", "192.168.1.1", "/24");
    router.port("G0/1").setIpv4(Lab.ip("10.0.0.1"), Lab.mask("/8")); // still shut down
    Lab.cable(topology, router, "G0/0", sw, "Fa0/1");
    Lab.cable(topology, router, "G0/1", topology.addSwitch("SW2"), "Fa0/1");
    SimulationEngine engine = new SimulationEngine(topology);

    assertThat(engine.routingTable(router.id()))
        .containsExactly(new Route(Ipv4Network.parse("192.168.1.0/24"), "G0/0"));

    router.port("G0/1").setAdminUp(true);
    assertThat(engine.routingTable(router.id()))
        .containsExactly(
            new Route(Ipv4Network.parse("10.0.0.0/8"), "G0/1"),
            new Route(Ipv4Network.parse("192.168.1.0/24"), "G0/0"));

    router.port("G0/0").clearIpv4();
    assertThat(engine.routingTable(router.id()))
        .extracting(Route::interfaceName)
        .containsExactly("G0/1");
  }

  @Test
  void anInterfaceWithNoCableHasNoRoute() {
    Router router = topology.addRouter("R1");
    Lab.configure(router, "G0/0", "192.168.1.1", "/24");

    assertThat(new SimulationEngine(topology).routingTable(router.id())).isEmpty();
  }

  // ---- behaviour that follows from the settings -----------------------------------------------

  @Test
  void theHostTtlIsConfigurable() {
    Lan lan = lan();
    SimulationEngine engine =
        new SimulationEngine(topology, SimulationSettings.defaults().withHostTtl(64));

    PingResult result = engine.ping(lan.a().id(), Lab.ip("192.168.1.20"), 2);

    SimEvent received =
        result.events().stream()
            .filter(e -> e.explanationKey().equals("sim.icmp.echoReplyReceived"))
            .findFirst()
            .orElseThrow();
    assertThat(received.args()).containsExactly("192.168.1.20", 1, 64);
  }

  @Test
  void aServerPingsLikeAPc() {
    Host server = topology.addServer("Web");
    server.configure(Lab.ip("192.168.1.5"), Lab.mask("/24"));
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Lab.cable(topology, server, "Fa0", b, "Fa0");

    PingResult result = new SimulationEngine(topology).ping(server.id(), Lab.ip("192.168.1.20"), 2);

    assertThat(result.attempts().get(1).success()).isTrue();
  }

  // ---- determinism and the result's shape -----------------------------------------------------

  @Test
  void theSameTopologyAndCallsGiveTheSameEventsEveryTime() {
    PingResult first = deterministicRun();
    PingResult second = deterministicRun();

    assertThat(second.events()).isEqualTo(first.events());
    assertThat(second.attempts()).isEqualTo(first.attempts());
    assertThat(first.events()).isNotEmpty();
  }

  private static PingResult deterministicRun() {
    Topology t = new Topology(99);
    Host a = Lab.pc(t, "A", "192.168.1.10", "192.168.1.1");
    Host b = t.addPc("B");
    b.configure(Lab.ip("192.168.2.10"), Lab.mask("/24"), Lab.ip("192.168.2.1"));
    Router r = t.addRouter("R");
    Lab.configure(r, "G0/0", "192.168.1.1", "/24");
    Lab.configure(r, "G0/1", "192.168.2.1", "/24");
    Lab.cable(t, a, "Fa0", r, "G0/0");
    Lab.cable(t, b, "Fa0", r, "G0/1");
    return new SimulationEngine(t).ping(a.id(), Lab.ip("192.168.2.10"), 4);
  }

  @Test
  void eventTimesNeverDecreaseAndEveryEventBelongsToADevice() {
    PingResult result = deterministicRun();

    long previous = 0;
    for (SimEvent event : result.events()) {
      assertThat(event.time()).isGreaterThanOrEqualTo(previous);
      previous = event.time();
      assertThat(event.layer()).isBetween(1, 7);
    }
  }

  @Test
  void everyEventTheEngineEmitsHasADocumentedKey() {
    PingResult result = deterministicRun();
    List<String> known =
        java.util.Arrays.stream(ExplanationKey.values()).map(ExplanationKey::key).toList();

    assertThat(result.events()).allSatisfy(e -> assertThat(known).contains(e.explanationKey()));
  }

  @Test
  void eachPingRunsOnItsOwnClockAndUsesItsOwnIcmpIdentifier() {
    Lan lan = lan();

    PingResult first = lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 2);
    PingResult second = lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 2);

    assertThat(second.events().get(0).time()).isZero();
    assertThat(icmpId(first)).isEqualTo(1);
    assertThat(icmpId(second)).isEqualTo(2);
  }

  private static int icmpId(PingResult result) {
    return result.events().stream()
        .map(SimEvent::pdu)
        .filter(Ipv4Packet.class::isInstance)
        .map(pdu -> (IcmpMessage) ((Ipv4Packet) pdu).payload())
        .findFirst()
        .orElseThrow()
        .id();
  }

  @Test
  void theResultCannotBeChanged() {
    Lan lan = lan();
    PingResult result = lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 2);

    assertThatThrownBy(() -> result.events().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.attempts().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(result.sourceDeviceId()).isEqualTo(lan.a().id());
    assertThat(result.destination()).isEqualTo(Lab.ip("192.168.1.20"));
    assertThat(result.eventLimitReached()).isFalse();
  }

  @Test
  void theTopologyCanBeEditedBetweenPings() {
    Lan lan = lan();
    assertThat(lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 2).reachable()).isTrue();

    lan.b().configure(Lab.ip("192.168.1.21"), Lab.mask("/24")); // B changes its address
    PingResult old = lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 1);
    PingResult renewed = lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.21"), 2);

    // The old cache entry still sends the frame to B's MAC, but B no longer owns that address.
    assertThat(old.reachable()).isFalse();
    assertThat(old.events()).anyMatch(e -> e.explanationKey().equals("sim.host.packetNotForMe"));
    assertThat(renewed.attempts().get(1).success()).isTrue();
  }
}
