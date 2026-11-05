package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mrramez.netlabstudio.core.model.CableRules;
import io.github.mrramez.netlabstudio.core.model.CableType;
import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Hub;
import io.github.mrramez.netlabstudio.core.model.Link;
import io.github.mrramez.netlabstudio.core.model.LinkState;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.model.Switch;
import io.github.mrramez.netlabstudio.core.model.Topology;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Why a ping fails, and that the explanation says so (CURRICULUM §4.5 and SPEC §5). */
class FailureScenariosTest {

  private final Topology topology = new Topology();

  private static Optional<SimEvent> find(PingResult result, String key) {
    return result.events().stream().filter(e -> e.explanationKey().equals(key)).findFirst();
  }

  private static boolean anyFrameSent(PingResult result) {
    return result.events().stream().anyMatch(e -> e.action().isTransmission());
  }

  /** PC A - R1 - PC B, with the routers' interfaces configured as in the lab. */
  private record Routed(Host a, Host b, Router r1, SimulationEngine engine) {}

  private Routed routed() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.1");
    Host b = topology.addPc("B");
    b.configure(Lab.ip("192.168.2.10"), Lab.mask("/24"), Lab.ip("192.168.2.1"));
    Router r1 = topology.addRouter("R1");
    Lab.configure(r1, "G0/0", "192.168.1.1", "/24");
    Lab.configure(r1, "G0/1", "192.168.2.1", "/24");
    Lab.cable(topology, a, "Fa0", r1, "G0/0");
    Lab.cable(topology, b, "Fa0", r1, "G0/1");
    return new Routed(a, b, r1, new SimulationEngine(topology));
  }

  // ---- failures at the source host ------------------------------------------------------------

  @Test
  void noDefaultGatewayFailsAtTheSourceAndSendsNothing() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Switch sw = topology.addSwitch("SW");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.2.10"), 2);

    assertThat(result.reachable()).isFalse();
    assertThat(result.attempts())
        .allSatisfy(
            attempt -> {
              assertThat(attempt.reason()).contains(FailureReason.NO_DEFAULT_GATEWAY);
              assertThat(attempt.failedAtDeviceId()).isEqualTo(a.id());
            });
    assertThat(anyFrameSent(result)).isFalse();
    SimEvent why = find(result, "sim.host.noDefaultGateway").orElseThrow();
    assertThat(why.action()).isEqualTo(SimAction.DROP);
    assertThat(why.layer()).isEqualTo(3);
    // destination IP, and the host's own network that it does not match
    assertThat(why.args()).containsExactly("192.168.2.10", "192.168.1.0");
    assertThat(result.events().get(result.events().size() - 1).explanationKey())
        .isEqualTo("sim.ping.timeout");
  }

  @Test
  void aGatewayOutsideTheOwnNetworkCannotBeReached() {
    Host a = topology.addPc("A");
    a.configure(Lab.ip("192.168.1.10"), Lab.mask("/24"), Lab.ip("192.168.2.1"));

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("10.0.0.5"), 1);

    assertThat(result.attempts().get(0).reason())
        .contains(FailureReason.GATEWAY_NOT_ON_LOCAL_SUBNET);
    SimEvent why = find(result, "sim.host.gatewayNotOnLocalSubnet").orElseThrow();
    // gateway, own IP, mask, own network, gateway network (gateway AND mask)
    assertThat(why.args())
        .containsExactly(
            "192.168.2.1", "192.168.1.10", "255.255.255.0", "192.168.1.0", "192.168.2.0");
    assertThat(anyFrameSent(result)).isFalse();
  }

  @Test
  void aHostWithoutAnAddressCannotPing() {
    Host a = topology.addPc("A");

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("10.0.0.5"), 1);

    assertThat(result.attempts().get(0).reason()).contains(FailureReason.NO_IP_ADDRESS);
    assertThat(find(result, "sim.host.noIpAddress")).isPresent();
  }

  @Test
  void aHostCanPingItself() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.10"), 2);
    PingResult loopback = new SimulationEngine(topology).ping(a.id(), Lab.ip("127.0.0.1"), 1);

    assertThat(result.allReceived()).isTrue();
    assertThat(loopback.allReceived()).isTrue();
    assertThat(anyFrameSent(result)).isFalse();
    assertThat(find(result, "sim.ping.self")).isPresent();
    assertThat(result.attempts().get(0).roundTripTicks()).isZero();
  }

  // ---- cables ---------------------------------------------------------------------------------

  @Test
  void aStraightCableBetweenTwoPcsKeepsTheLinkDownInStrictMode() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Link link = topology.connect(a.nic(), b.nic(), CableType.STRAIGHT);

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.20"), 1);

    assertThat(link.state()).isEqualTo(LinkState.DOWN);
    assertThat(result.attempts().get(0).reason()).contains(FailureReason.WRONG_CABLE);
    SimEvent why = find(result, "model.link.wrongCable").orElseThrow();
    assertThat(why.layer()).isEqualTo(1);
    // The hint names the cable to use instead.
    assertThat(why.args()).containsExactly("A Fa0", "MDI", "B Fa0", "MDI", "STRAIGHT", "CROSSOVER");
    assertThat(anyFrameSent(result)).isFalse();
  }

  @Test
  void theSamePcsPingWithTheRightCableOrWithAutoMdix() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Link link = topology.connect(a.nic(), b.nic(), CableType.STRAIGHT);
    SimulationEngine engine = new SimulationEngine(topology);
    assertThat(engine.ping(a.id(), Lab.ip("192.168.1.20"), 1).reachable()).isFalse();

    topology.setCableRules(CableRules.autoMdix());
    assertThat(engine.ping(a.id(), Lab.ip("192.168.1.20"), 2).reachable()).isTrue();

    topology.setCableRules(CableRules.strictCabling());
    topology.disconnect(link);
    topology.connect(a.nic(), b.nic(), CableType.CROSSOVER);
    assertThat(engine.ping(a.id(), Lab.ip("192.168.1.20"), 1).reachable()).isTrue();
  }

  @Test
  void aPcWithoutACableCannotSend() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.20"), 1);

    assertThat(result.attempts().get(0).reason()).contains(FailureReason.NOT_CONNECTED);
    assertThat(find(result, "model.link.notConnected").orElseThrow().args())
        .containsExactly("A Fa0");
  }

  @Test
  void aCableThatCannotBelongThereMakesTheLinkIncompatible() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Router r = topology.addRouter("R", 1, 1);
    r.port("S0/0/0").setAdminUp(true);
    topology.connect(a.nic(), r.port("S0/0/0"), CableType.SERIAL);

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.20"), 1);

    assertThat(result.attempts().get(0).reason()).contains(FailureReason.WRONG_CABLE);
    assertThat(find(result, "model.link.incompatiblePorts")).isPresent();
  }

  // ---- router interface states ----------------------------------------------------------------

  @Test
  void aRouterInterfaceThatIsStillShutDownMakesTheLinkAmberAndExplainsNoShutdown() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.1");
    Router r1 = topology.addRouter("R1");
    r1.port("G0/0").setIpv4(Lab.ip("192.168.1.1"), Lab.mask("/24")); // but no "no shutdown"
    Link link = Lab.cable(topology, a, "Fa0", r1, "G0/0");

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.1"), 2);

    assertThat(link.state()).isEqualTo(LinkState.ADMIN_DOWN);
    assertThat(result.reachable()).isFalse();
    assertThat(result.attempts())
        .allSatisfy(
            attempt -> {
              assertThat(attempt.reason()).contains(FailureReason.INTERFACE_DOWN);
              assertThat(attempt.failedAtDeviceId()).isEqualTo(a.id());
            });
    // The dedicated "router peer" message is the one that tells the student to use "no shutdown".
    SimEvent why = find(result, "model.link.adminDownPeerRouter").orElseThrow();
    assertThat(why.args()).containsExactly("R1", "G0/0");
    assertThat(anyFrameSent(result)).isFalse();

    // After "no shutdown" the same ping works.
    r1.port("G0/0").setAdminUp(true);
    assertThat(link.state()).isEqualTo(LinkState.UP);
    assertThat(new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.1"), 2).reachable())
        .isTrue();
  }

  @Test
  void aShutDownSwitchPortIsAlsoReportedAsAmber() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Switch sw = topology.addSwitch("SW");
    Link link = Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    sw.port("Fa0/1").setAdminUp(false);

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.20"), 1);

    assertThat(link.state()).isEqualTo(LinkState.ADMIN_DOWN);
    assertThat(result.attempts().get(0).reason()).contains(FailureReason.INTERFACE_DOWN);
    assertThat(find(result, "model.link.adminDownPeer")).isPresent();
  }

  @Test
  void aDisabledNicIsReportedAsDownLocally() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Switch sw = topology.addSwitch("SW");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    a.nic().setAdminUp(false);

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.20"), 1);

    assertThat(result.attempts().get(0).reason()).contains(FailureReason.INTERFACE_DOWN);
    assertThat(find(result, "model.link.adminDownLocal")).isPresent();
  }

  @Test
  void theFarInterfaceOfARouterThatIsShutDownGivesNoRouteWithAHint() {
    Routed lab = routed();
    lab.r1().port("G0/1").setAdminUp(false);
    lab.engine().ping(lab.a().id(), Lab.ip("192.168.1.1"), 1); // warm up ARP towards the router

    PingResult result = lab.engine().ping(lab.a().id(), Lab.ip("192.168.2.10"), 1);

    PingAttempt attempt = result.attempts().get(0);
    assertThat(attempt.reason()).contains(FailureReason.INTERFACE_DOWN);
    assertThat(attempt.failedAtDeviceId()).isEqualTo(lab.r1().id());
    // The router answered with ICMP "destination unreachable", code 0.
    assertThat(attempt.repliedFrom()).isEqualTo(Lab.ip("192.168.1.1"));
    SimEvent why = find(result, "sim.router.noRouteInterfaceShutdown").orElseThrow();
    assertThat(why.args()).containsExactly("192.168.2.10", "G0/1");
    assertThat(find(result, "sim.icmp.unreachableCreated").orElseThrow().args())
        .containsExactly("192.168.1.10", 0);
    assertThat(find(result, "sim.icmp.errorReceived").orElseThrow().args())
        .containsExactly("192.168.1.1", "DESTINATION_UNREACHABLE", 0);
    assertThat(result.events().get(result.events().size() - 1).explanationKey())
        .isEqualTo("sim.ping.errorReply");
    // And the routing table has lost the connected route.
    assertThat(lab.engine().routingTable(lab.r1().id()))
        .extracting(Route::interfaceName)
        .containsExactly("G0/0");
  }

  @Test
  void aWrongCableOnTheFarInterfaceGivesTheLinkDownVariantOfTheHint() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.1");
    Host b = topology.addPc("B");
    b.configure(Lab.ip("192.168.2.10"), Lab.mask("/24"), Lab.ip("192.168.2.1"));
    Router r1 = topology.addRouter("R1");
    Lab.configure(r1, "G0/0", "192.168.1.1", "/24");
    Lab.configure(r1, "G0/1", "192.168.2.1", "/24");
    Lab.cable(topology, a, "Fa0", r1, "G0/0");
    topology.connect(b.nic(), r1.port("G0/1"), CableType.STRAIGHT); // PC and router both MDI
    SimulationEngine engine = new SimulationEngine(topology);
    engine.ping(a.id(), Lab.ip("192.168.1.1"), 1);

    PingResult result = engine.ping(a.id(), Lab.ip("192.168.2.10"), 1);

    assertThat(result.attempts().get(0).reason()).contains(FailureReason.INTERFACE_DOWN);
    assertThat(find(result, "sim.router.noRouteInterfaceDown").orElseThrow().args())
        .containsExactly("192.168.2.10", "G0/1");
  }

  // ---- routing --------------------------------------------------------------------------------

  @Test
  void aDestinationNoInterfaceCoversGivesNoRoute() {
    Routed lab = routed();
    lab.engine().ping(lab.a().id(), Lab.ip("192.168.1.1"), 1);

    PingResult result = lab.engine().ping(lab.a().id(), Lab.ip("10.9.9.9"), 1);

    PingAttempt attempt = result.attempts().get(0);
    assertThat(attempt.reason()).contains(FailureReason.NO_ROUTE);
    assertThat(attempt.failedAtDeviceId()).isEqualTo(lab.r1().id());
    assertThat(find(result, "sim.router.noRoute").orElseThrow().args()).containsExactly("10.9.9.9");
    assertThat(attempt.repliedFrom()).isEqualTo(Lab.ip("192.168.1.1"));
  }

  @Test
  void theTtlRunsOutAtTheRouterWhichReportsTimeExceeded() {
    SimulationSettings settings = SimulationSettings.defaults().withHostTtl(1);
    Routed lab = routed();
    SimulationEngine engine = new SimulationEngine(topology, settings);
    engine.ping(lab.a().id(), Lab.ip("192.168.1.1"), 2); // fill ARP; TTL 1 is enough on the LAN

    PingResult result = engine.ping(lab.a().id(), Lab.ip("192.168.2.10"), 1);

    PingAttempt attempt = result.attempts().get(0);
    assertThat(attempt.reason()).contains(FailureReason.TTL_EXPIRED);
    assertThat(attempt.failedAtDeviceId()).isEqualTo(lab.r1().id());
    assertThat(attempt.repliedFrom()).isEqualTo(Lab.ip("192.168.1.1"));
    assertThat(find(result, "sim.router.ttlDecrement").orElseThrow().args()).containsExactly(1, 0);
    SimEvent expired = find(result, "sim.router.ttlExpired").orElseThrow();
    assertThat(expired.action()).isEqualTo(SimAction.DROP);
    assertThat(expired.args()).containsExactly("192.168.1.10", "192.168.2.10");
    assertThat(find(result, "sim.icmp.timeExceededCreated")).isPresent();
    assertThat(find(result, "sim.icmp.errorReceived").orElseThrow().args())
        .containsExactly("192.168.1.1", "TIME_EXCEEDED", 0);
    // The packet never reached B's LAN.
    assertThat(result.events()).noneMatch(e -> e.deviceId().equals(lab.b().id()));
  }

  @Test
  void aTtlOfOneIsEnoughOnTheSameNetwork() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Switch sw = topology.addSwitch("SW");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    SimulationEngine engine =
        new SimulationEngine(topology, SimulationSettings.defaults().withHostTtl(1));

    assertThat(engine.ping(a.id(), Lab.ip("192.168.1.20"), 2).received()).isEqualTo(1);
  }

  @Test
  void aReplyThatCannotFindItsWayBackIsAFailureAtTheReplier() {
    // B has no default gateway, so it can receive A's echo request but cannot answer across
    // routers.
    Routed lab = routed();
    lab.b().clearDefaultGateway();

    PingResult result = lab.engine().ping(lab.a().id(), Lab.ip("192.168.2.10"), 3);

    assertThat(result.reachable()).isFalse();
    PingAttempt last = result.attempts().get(2);
    assertThat(last.reason()).contains(FailureReason.NO_DEFAULT_GATEWAY);
    assertThat(last.failedAtDeviceId()).isEqualTo(lab.b().id());
    assertThat(find(result, "sim.icmp.echoRequestReceived")).isPresent();
  }

  // ---- ARP that nobody answers ----------------------------------------------------------------

  @Test
  void anAddressNobodyOwnsGivesHostUnreachableAfterTheArpTimeout() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Switch sw = topology.addSwitch("SW");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.99"), 2);

    assertThat(result.attempts())
        .allSatisfy(
            attempt -> {
              // The first packet is lost to ARP, but "nobody answered" is the better explanation.
              assertThat(attempt.reason()).contains(FailureReason.HOST_UNREACHABLE);
              assertThat(attempt.failedAtDeviceId()).isEqualTo(a.id());
            });
    List<SimEvent> noReply =
        result.events().stream().filter(e -> e.explanationKey().equals("sim.arp.noReply")).toList();
    assertThat(noReply).hasSize(2);
    assertThat(noReply.get(0).action()).isEqualTo(SimAction.TIMEOUT);
    assertThat(noReply.get(0).args()).containsExactly("192.168.1.99");
  }

  @Test
  void aRouterThatCannotFindTheHostSendsHostUnreachable() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.1");
    Router r1 = topology.addRouter("R1");
    Switch lan2 = topology.addSwitch("LAN2");
    Lab.configure(r1, "G0/0", "192.168.1.1", "/24");
    Lab.configure(r1, "G0/1", "192.168.2.1", "/24");
    Lab.cable(topology, a, "Fa0", r1, "G0/0");
    Lab.cable(topology, r1, "G0/1", lan2, "Fa0/1"); // nobody else on LAN 2
    SimulationEngine engine = new SimulationEngine(topology);
    engine.ping(a.id(), Lab.ip("192.168.1.1"), 1);

    PingResult result = engine.ping(a.id(), Lab.ip("192.168.2.50"), 1);

    PingAttempt attempt = result.attempts().get(0);
    assertThat(attempt.reason()).contains(FailureReason.HOST_UNREACHABLE);
    assertThat(attempt.failedAtDeviceId()).isEqualTo(r1.id());
    assertThat(attempt.repliedFrom()).isEqualTo(Lab.ip("192.168.1.1"));
    assertThat(find(result, "sim.arp.packetHeld")).isPresent();
    assertThat(find(result, "sim.arp.heldPacketDiscarded").orElseThrow().args())
        .containsExactly("192.168.2.50");
    assertThat(find(result, "sim.icmp.unreachableCreated").orElseThrow().args())
        .containsExactly("192.168.1.10", 1);
    assertThat(find(result, "sim.icmp.errorReceived").orElseThrow().args())
        .containsExactly("192.168.1.1", "DESTINATION_UNREACHABLE", 1);
  }

  // ---- hosts and routers do not do each other's jobs -------------------------------------------

  @Test
  void aHostDoesNotForwardPacketsThatAreNotForIt() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.20");
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Lab.cable(topology, a, "Fa0", b, "Fa0");
    SimulationEngine engine = new SimulationEngine(topology);

    // B is A's "gateway" but is only a PC.
    PingResult result = engine.ping(a.id(), Lab.ip("192.168.2.10"), 2);

    assertThat(result.reachable()).isFalse();
    SimEvent drop = find(result, "sim.host.packetNotForMe").orElseThrow();
    assertThat(drop.deviceId()).isEqualTo(b.id());
    assertThat(drop.args()).containsExactly("192.168.2.10", "192.168.1.20");
  }

  @Test
  void aRouterIgnoresAnArpRequestForSomeoneElseInsteadOfForwardingIt() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.5"); // .5 is nobody
    Router r1 = topology.addRouter("R1");
    Lab.configure(r1, "G0/0", "192.168.1.1", "/24");
    Lab.configure(r1, "G0/1", "192.168.2.1", "/24");
    Host b = Lab.pc(topology, "B", "192.168.2.10", "192.168.2.1");
    Lab.cable(topology, a, "Fa0", r1, "G0/0");
    Lab.cable(topology, b, "Fa0", r1, "G0/1");

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.2.10"), 1);

    SimEvent drop = find(result, "sim.router.broadcastNotForwarded").orElseThrow();
    assertThat(drop.deviceId()).isEqualTo(r1.id());
    assertThat(drop.portName()).isEqualTo("G0/0");
    assertThat(result.events()).noneMatch(e -> e.deviceId().equals(b.id()));
  }

  @Test
  void aRouterInterfaceWithoutAnAddressCannotHandleTraffic() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.1");
    Router r1 = topology.addRouter("R1");
    r1.port("G0/0").setAdminUp(true); // up, but no IP address
    Lab.cable(topology, a, "Fa0", r1, "G0/0");

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.1"), 1);

    assertThat(find(result, "sim.router.interfaceNoIp").orElseThrow().args())
        .containsExactly("G0/0");
    assertThat(result.reachable()).isFalse();
  }

  @Test
  void aRouterOnAHubIgnoresFramesForOtherStations() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.1");
    Host b = Lab.pc(topology, "B", "192.168.1.20", "192.168.1.1");
    Router r1 = topology.addRouter("R1");
    Lab.configure(r1, "G0/0", "192.168.1.1", "/24");
    Hub hub = topology.addHub("HUB");
    Lab.cable(topology, a, "Fa0", hub, "Port1");
    Lab.cable(topology, b, "Fa0", hub, "Port2");
    Lab.cable(topology, r1, "G0/0", hub, "Port3");

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.20"), 2);

    assertThat(result.attempts().get(1).success()).isTrue();
    assertThat(result.events())
        .filteredOn(e -> e.deviceId().equals(r1.id()) && e.action() == SimAction.DROP)
        .extracting(SimEvent::explanationKey)
        .contains("sim.nic.frameNotForMe");
  }
}
