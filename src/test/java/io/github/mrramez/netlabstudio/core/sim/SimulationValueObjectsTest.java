package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mrramez.netlabstudio.core.model.CableType;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.model.Switch;
import io.github.mrramez.netlabstudio.core.model.Topology;
import io.github.mrramez.netlabstudio.core.net.ExplanationStep;
import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.Ipv4Network;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import io.github.mrramez.netlabstudio.core.net.SubnetMask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** SimEvent, results, settings, table rows and the explanation-key contract. */
class SimulationValueObjectsTest {

  private static final ArpMessage ARP =
      ArpMessage.request(
          MacAddress.parse("02:00:00:00:00:0a"),
          Ipv4Address.parse("192.168.1.10"),
          Ipv4Address.parse("192.168.1.20"));

  // ---- SimEvent ----

  @Test
  void anEventKeepsWhatHappened() {
    SimEvent event =
        new SimEvent(
            3,
            "d1",
            "Fa0",
            2,
            SimAction.RECEIVE,
            ARP,
            "sim.arp.requestForMe",
            List.of("192.168.1.10", 7));

    assertThat(event.time()).isEqualTo(3);
    assertThat(event.hasPort()).isTrue();
    assertThat(event.hasPdu()).isTrue();
    assertThat(event.explanation())
        .isEqualTo(ExplanationStep.of("sim.arp.requestForMe", "192.168.1.10", 7));
    assertThat(event.frame()).isEmpty();
  }

  @Test
  void anEventAboutTheWholeDeviceHasNoPortOrPdu() {
    SimEvent event =
        new SimEvent(0, "d1", null, 7, SimAction.GENERATE, null, "sim.ping.start", List.of());

    assertThat(event.hasPort()).isFalse();
    assertThat(event.hasPdu()).isFalse();
  }

  @Test
  void anEventExposesTheFrameItShows() {
    EthernetFrame frame = EthernetFrame.arp(ARP.senderMac(), MacAddress.BROADCAST, ARP);
    SimEvent event =
        new SimEvent(0, "d1", "Fa0", 2, SimAction.TRANSMIT, frame, "sim.l2.frameSent", List.of());

    assertThat(event.frame()).contains(frame);
  }

  @Test
  void anEventCopiesItsArgumentsSoItCannotChangeLater() {
    List<Object> args = new ArrayList<>(List.of("x"));
    SimEvent event = new SimEvent(0, "d1", null, 3, SimAction.DROP, null, "sim.ping.start", args);
    args.add("y");

    assertThat(event.args()).containsExactly("x");
    assertThatThrownBy(() -> event.args().add("z"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void anEventRejectsBadFields() {
    assertThatThrownBy(
            () ->
                new SimEvent(-1, "d1", null, 3, SimAction.DROP, null, "sim.ping.start", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new SimEvent(0, null, null, 3, SimAction.DROP, null, "sim.ping.start", List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new SimEvent(0, " ", null, 3, SimAction.DROP, null, "sim.ping.start", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new SimEvent(0, "d1", null, 0, SimAction.DROP, null, "sim.ping.start", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new SimEvent(0, "d1", null, 8, SimAction.DROP, null, "sim.ping.start", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new SimEvent(0, "d1", null, 3, null, null, "sim.ping.start", List.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new SimEvent(0, "d1", null, 3, SimAction.DROP, null, "not a key", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new SimEvent(
                    0, "d1", null, 3, SimAction.DROP, null, "sim.ping.start", List.of(1.5)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void onlyTransmitAndRepeatPutFramesOnACable() {
    assertThat(Arrays.stream(SimAction.values()).filter(SimAction::isTransmission))
        .containsExactly(SimAction.TRANSMIT, SimAction.REPEAT);
  }

  // ---- the key contract ----

  @Test
  void everyExplanationKeyIsUniqueAndWellFormed() {
    Set<String> seen = new HashSet<>();
    for (ExplanationKey key : ExplanationKey.values()) {
      assertThat(seen.add(key.key())).as("duplicate key %s", key.key()).isTrue();
      assertThat(key.key()).startsWith("sim.");
      // The ExplanationStep rules (dotted lower-camel segments) are the ones the bundles rely on.
      assertThat(new ExplanationStep(key.key(), List.of()).key()).isEqualTo(key.key());
    }
    assertThat(seen).hasSize(ExplanationKey.values().length);
  }

  // ---- ping results ----

  @Test
  void aSuccessfulAttemptNeedsTheAddressItWasAnsweredFrom() {
    PingAttempt ok = new PingAttempt(1, null, null, Ipv4Address.parse("10.0.0.1"), 4);

    assertThat(ok.success()).isTrue();
    assertThat(ok.reason()).isEmpty();
    assertThatThrownBy(() -> new PingAttempt(1, null, null, null, 4))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void aFailedAttemptNeedsToSayWhere() {
    PingAttempt failed = new PingAttempt(2, FailureReason.NO_ROUTE, "d3", null, 0);

    assertThat(failed.success()).isFalse();
    assertThat(failed.reason()).contains(FailureReason.NO_ROUTE);
    assertThatThrownBy(() -> new PingAttempt(2, FailureReason.NO_ROUTE, null, null, 0))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PingAttempt(0, FailureReason.NO_ROUTE, "d3", null, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPingResultCountsSentReceivedAndLost() {
    Ipv4Address target = Ipv4Address.parse("10.0.0.1");
    PingResult result =
        new PingResult(
            "d1",
            target,
            List.of(
                new PingAttempt(1, FailureReason.LOST_TO_ARP, "d1", null, 0),
                new PingAttempt(2, null, null, target, 4),
                new PingAttempt(3, null, null, target, 4)),
            List.of(),
            false);

    assertThat(result.sent()).isEqualTo(3);
    assertThat(result.received()).isEqualTo(2);
    assertThat(result.lost()).isEqualTo(1);
    assertThat(result.reachable()).isTrue();
    assertThat(result.allReceived()).isFalse();
  }

  @Test
  void aPingResultWithNoAnswersIsNotReachable() {
    PingResult result =
        new PingResult(
            "d1",
            Ipv4Address.parse("10.0.0.1"),
            List.of(new PingAttempt(1, FailureReason.NO_REPLY, "d1", null, 0)),
            List.of(),
            false);

    assertThat(result.reachable()).isFalse();
    assertThat(result.allReceived()).isFalse();
    assertThatThrownBy(
            () -> new PingResult(null, Ipv4Address.parse("10.0.0.1"), List.of(), List.of(), false))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PingResult("d1", null, List.of(), List.of(), false))
        .isInstanceOf(NullPointerException.class);
  }

  // ---- settings ----

  @Test
  void theDefaultsAreThoseOfWindowsHostsAndCiscoRouters() {
    SimulationSettings defaults = SimulationSettings.defaults();

    assertThat(defaults.maxEvents())
        .isEqualTo(SimulationSettings.DEFAULT_MAX_EVENTS)
        .isEqualTo(5_000);
    assertThat(defaults.hostTtl()).isEqualTo(128);
    assertThat(defaults.routerTtl()).isEqualTo(255);
    assertThat(defaults.routerDropsFirstPacketWhileArping()).isTrue();
  }

  @Test
  void settingsAreChangedByCopying() {
    SimulationSettings base = SimulationSettings.defaults();
    SimulationSettings changed =
        base.withMaxEvents(100)
            .withHostTtl(64)
            .withRouterTtl(250)
            .withRouterDropsFirstPacketWhileArping(false);

    assertThat(changed).isEqualTo(new SimulationSettings(100, 64, 250, false));
    assertThat(base).isEqualTo(SimulationSettings.defaults());
  }

  @Test
  void settingsRejectValuesOutOfRange() {
    assertThatThrownBy(() -> SimulationSettings.defaults().withMaxEvents(9))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SimulationSettings.defaults().withMaxEvents(1_000_001))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SimulationSettings.defaults().withHostTtl(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SimulationSettings.defaults().withHostTtl(256))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SimulationSettings.defaults().withRouterTtl(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            SimulationSettings.defaults()
                .withMaxEvents(10)
                .withHostTtl(1)
                .withRouterTtl(255)
                .maxEvents())
        .isEqualTo(10);
  }

  // ---- table rows ----

  @Test
  void tableRowsRejectMissingParts() {
    MacAddress mac = MacAddress.parse("02:00:00:00:00:0a");
    Ipv4Address ip = Ipv4Address.parse("10.0.0.1");

    assertThatThrownBy(() -> new MacEntry(null, "Fa0/1")).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new MacEntry(mac, null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ArpEntry(null, mac, "Fa0"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ArpEntry(ip, null, "Fa0"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ArpEntry(ip, mac, null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Route(null, "G0/0")).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Route(Ipv4Network.parse("10.0.0.0/8"), null))
        .isInstanceOf(NullPointerException.class);
  }

  // ---- routing tables ----

  @Test
  void theLongestPrefixWins() {
    List<Route> routes =
        List.of(
            new Route(Ipv4Network.parse("10.0.0.0/8"), "G0/0"),
            new Route(Ipv4Network.parse("10.1.0.0/16"), "G0/1"),
            new Route(Ipv4Network.parse("10.1.2.0/24"), "G0/2"));

    assertThat(RoutingTables.longestMatch(routes, Ipv4Address.parse("10.1.2.3")))
        .map(Route::interfaceName)
        .contains("G0/2");
    assertThat(RoutingTables.longestMatch(routes, Ipv4Address.parse("10.1.9.9")))
        .map(Route::interfaceName)
        .contains("G0/1");
    assertThat(RoutingTables.longestMatch(routes, Ipv4Address.parse("10.200.0.1")))
        .map(Route::interfaceName)
        .contains("G0/0");
    assertThat(RoutingTables.longestMatch(routes, Ipv4Address.parse("11.0.0.1"))).isEmpty();
    // Order in the list does not matter.
    assertThat(
            RoutingTables.longestMatch(
                List.of(routes.get(2), routes.get(0)), Ipv4Address.parse("10.1.2.3")))
        .map(Route::interfaceName)
        .contains("G0/2");
  }

  @Test
  void connectedRoutesAreSortedByNetworkThenPrefixLength() {
    Topology topology = new Topology();
    Router router = topology.addRouter("R", 4, 0);
    String[][] interfaces = {
      {"G0/0", "192.168.5.1", "/24"},
      {"G0/1", "10.0.0.1", "/8"},
      {"G0/2", "172.16.0.1", "/16"},
      {"G0/3", "10.0.0.2", "/9"},
    };
    for (int i = 0; i < interfaces.length; i++) {
      Switch sw = topology.addSwitch("SW" + i);
      topology.connect(router.port(interfaces[i][0]), sw.port("Fa0/1"), CableType.STRAIGHT);
      router.port(interfaces[i][0]).setAdminUp(true);
      router
          .port(interfaces[i][0])
          .setIpv4(Ipv4Address.parse(interfaces[i][1]), SubnetMask.parse(interfaces[i][2]));
    }

    List<Route> routes = RoutingTables.connected(router);

    assertThat(routes)
        .extracting(r -> r.destination().toString())
        .containsExactly("10.0.0.0/8", "10.0.0.0/9", "172.16.0.0/16", "192.168.5.0/24");
  }
}
