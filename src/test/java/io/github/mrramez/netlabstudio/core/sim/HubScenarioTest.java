package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Hub;
import io.github.mrramez.netlabstudio.core.model.Topology;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** CURRICULUM §4.5: the same ping through a hub: every frame appears on every other port. */
class HubScenarioTest {

  private static final String EXPECTED_TRACE =
      """
      t=0 A L7 GENERATE sim.ping.start
      t=0 A L3 ENCAPSULATE sim.icmp.echoRequestCreated
      t=0 A L3 DECIDE sim.host.destinationLocal
      t=0 A.Fa0 L2 LOOKUP sim.arp.cacheMiss
      t=0 A.Fa0 L2 QUEUE sim.arp.packetHeld
      t=0 A.Fa0 L2 GENERATE sim.arp.requestSent
      t=0 A.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=1 HUB.Port1 L1 RECEIVE sim.l1.signalReceived
      t=1 HUB.Port2 L1 REPEAT sim.hub.repeat
      t=1 HUB.Port3 L1 REPEAT sim.hub.repeat
      t=2 B.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=2 B.Fa0 L2 DECAPSULATE sim.nic.acceptedBroadcast
      t=2 B.Fa0 L2 DELIVER sim.arp.requestForMe
      t=2 B.Fa0 L2 CACHE_UPDATE sim.arp.cacheUpdated
      t=2 B.Fa0 L2 GENERATE sim.arp.replySent
      t=2 B.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=2 C.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=2 C.Fa0 L2 DECAPSULATE sim.nic.acceptedBroadcast
      t=2 C.Fa0 L2 DROP sim.arp.notForMe
      t=3 HUB.Port2 L1 RECEIVE sim.l1.signalReceived
      t=3 HUB.Port1 L1 REPEAT sim.hub.repeat
      t=3 HUB.Port3 L1 REPEAT sim.hub.repeat
      t=4 A.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=4 A.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=4 A.Fa0 L2 DELIVER sim.arp.replyReceived
      t=4 A.Fa0 L2 CACHE_UPDATE sim.arp.cacheUpdated
      t=4 A.Fa0 L2 RELEASE sim.arp.packetReleased
      t=4 A.Fa0 L2 ENCAPSULATE sim.l2.encapsulated
      t=4 A.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=4 C.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=4 C.Fa0 L2 DROP sim.nic.frameNotForMe
      t=5 HUB.Port1 L1 RECEIVE sim.l1.signalReceived
      t=5 HUB.Port2 L1 REPEAT sim.hub.repeat
      t=5 HUB.Port3 L1 REPEAT sim.hub.repeat
      t=6 B.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=6 B.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=6 B.Fa0 L3 DELIVER sim.icmp.echoRequestReceived
      t=6 B L3 GENERATE sim.icmp.echoReplyCreated
      t=6 B L3 DECIDE sim.host.destinationLocal
      t=6 B.Fa0 L2 LOOKUP sim.arp.cacheHit
      t=6 B.Fa0 L2 ENCAPSULATE sim.l2.encapsulated
      t=6 B.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=6 C.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=6 C.Fa0 L2 DROP sim.nic.frameNotForMe
      t=7 HUB.Port2 L1 RECEIVE sim.l1.signalReceived
      t=7 HUB.Port1 L1 REPEAT sim.hub.repeat
      t=7 HUB.Port3 L1 REPEAT sim.hub.repeat
      t=8 A.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=8 A.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=8 A.Fa0 L3 DELIVER sim.icmp.echoReplyReceived
      t=8 C.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=8 C.Fa0 L2 DROP sim.nic.frameNotForMe
      t=8 A L7 DELIVER sim.ping.replyReceived
      t=9 A L7 GENERATE sim.ping.start
      t=9 A L3 ENCAPSULATE sim.icmp.echoRequestCreated
      t=9 A L3 DECIDE sim.host.destinationLocal
      t=9 A.Fa0 L2 LOOKUP sim.arp.cacheHit
      t=9 A.Fa0 L2 ENCAPSULATE sim.l2.encapsulated
      t=9 A.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=10 HUB.Port1 L1 RECEIVE sim.l1.signalReceived
      t=10 HUB.Port2 L1 REPEAT sim.hub.repeat
      t=10 HUB.Port3 L1 REPEAT sim.hub.repeat
      t=11 B.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=11 B.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=11 B.Fa0 L3 DELIVER sim.icmp.echoRequestReceived
      t=11 B L3 GENERATE sim.icmp.echoReplyCreated
      t=11 B L3 DECIDE sim.host.destinationLocal
      t=11 B.Fa0 L2 LOOKUP sim.arp.cacheHit
      t=11 B.Fa0 L2 ENCAPSULATE sim.l2.encapsulated
      t=11 B.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=11 C.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=11 C.Fa0 L2 DROP sim.nic.frameNotForMe
      t=12 HUB.Port2 L1 RECEIVE sim.l1.signalReceived
      t=12 HUB.Port1 L1 REPEAT sim.hub.repeat
      t=12 HUB.Port3 L1 REPEAT sim.hub.repeat
      t=13 A.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=13 A.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=13 A.Fa0 L3 DELIVER sim.icmp.echoReplyReceived
      t=13 C.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=13 C.Fa0 L2 DROP sim.nic.frameNotForMe
      t=13 A L7 DELIVER sim.ping.replyReceived
      """;

  private final Topology topology = new Topology();
  private Host a;
  private Host b;
  private Host c;
  private SimulationEngine engine;
  private PingResult result;

  @BeforeEach
  void buildAndPing() {
    a = Lab.pc(topology, "A", "192.168.1.10", null);
    b = Lab.pc(topology, "B", "192.168.1.20", null);
    c = Lab.pc(topology, "C", "192.168.1.30", null);
    Hub hub = topology.addHub("HUB");
    Lab.cable(topology, a, "Fa0", hub, "Port1");
    Lab.cable(topology, b, "Fa0", hub, "Port2");
    Lab.cable(topology, c, "Fa0", hub, "Port3");
    engine = new SimulationEngine(topology);
    result = engine.ping(a.id(), Lab.ip("192.168.1.20"), 2);
  }

  @Test
  void theEventSequenceIsExactlyThisOne() {
    assertThat(Lab.lines(topology, result.events())).isEqualTo(EXPECTED_TRACE.lines().toList());
  }

  @Test
  void bothPingsSucceedBecauseTheHostHoldsTheFirstPacketDuringArp() {
    assertThat(result.allReceived()).isTrue();
    assertThat(result.attempts().get(0).roundTripTicks()).isEqualTo(8);
    assertThat(result.attempts().get(1).roundTripTicks()).isEqualTo(4);
  }

  @Test
  void everyFrameFromAnyPcIsRepeatedOnBothOtherHubPorts() {
    // The hub repeats on "all other ports", so each of its three ports sees every frame once as
    // either the ingress or a REPEAT.
    Map<String, Long> repeats =
        result.events().stream()
            .filter(e -> e.action() == SimAction.REPEAT)
            .collect(Collectors.groupingBy(SimEvent::portName, Collectors.counting()));

    // Port1 repeats the frames of B (3: ARP reply, two echo replies); Port2 those of A (3: ARP
    // request, two echo requests); Port3 repeats frames from both sides.
    assertThat(repeats)
        .containsEntry("Port1", 3L)
        .containsEntry("Port2", 3L)
        .containsEntry("Port3", 6L);
  }

  @Test
  void theUninvolvedPcReceivesEveryFrameAndIgnoresAllButTheBroadcast() {
    List<SimEvent> received =
        result.events().stream()
            .filter(e -> e.deviceId().equals(c.id()) && e.action() == SimAction.RECEIVE)
            .toList();
    List<SimEvent> ignored =
        result.events().stream()
            .filter(e -> e.deviceId().equals(c.id()) && e.action() == SimAction.DROP)
            .toList();

    // ARP request, ARP reply, then two echo requests and two echo replies.
    assertThat(received).hasSize(6);
    assertThat(ignored)
        .extracting(SimEvent::explanationKey)
        .containsExactly(
            "sim.arp.notForMe",
            "sim.nic.frameNotForMe",
            "sim.nic.frameNotForMe",
            "sim.nic.frameNotForMe",
            "sim.nic.frameNotForMe",
            "sim.nic.frameNotForMe");
    // C's NIC discarded the unicast frames; its ARP cache stayed empty.
    assertThat(engine.arpTable(c.id())).isEmpty();
  }

  @Test
  void aHubLearnsNothing() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> engine.macTable(topology.devices().get(3).id()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(result.events()).noneMatch(e -> e.action() == SimAction.LEARN);
  }

  @Test
  void theHubWorksAtLayerOneOnly() {
    assertThat(result.events())
        .filteredOn(e -> topology.device(e.deviceId()).name().equals("HUB"))
        .allSatisfy(e -> assertThat(e.layer()).isEqualTo(1));
  }
}
