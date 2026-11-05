package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Switch;
import io.github.mrramez.netlabstudio.core.model.Topology;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CURRICULUM §4.5: PC 192.168.1.10/24 pings 192.168.1.20/24 through a switch. A third PC listens in
 * to show what a switch does not send it.
 */
class SwitchScenarioTest {

  private static final String EXPECTED_TRACE =
      """
      t=0 A L7 GENERATE sim.ping.start
      t=0 A L3 ENCAPSULATE sim.icmp.echoRequestCreated
      t=0 A L3 DECIDE sim.host.destinationLocal
      t=0 A.Fa0 L2 LOOKUP sim.arp.cacheMiss
      t=0 A.Fa0 L3 DROP sim.arp.firstPacketLost
      t=0 A.Fa0 L2 GENERATE sim.arp.requestSent
      t=0 A.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=1 SW.Fa0/1 L2 RECEIVE sim.l2.frameReceived
      t=1 SW.Fa0/1 L2 LEARN sim.switch.learnNew
      t=1 SW.Fa0/1 L2 FLOOD sim.switch.floodBroadcast
      t=1 SW.Fa0/2 L2 TRANSMIT sim.l2.frameSent
      t=1 SW.Fa0/3 L2 TRANSMIT sim.l2.frameSent
      t=2 B.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=2 B.Fa0 L2 DECAPSULATE sim.nic.acceptedBroadcast
      t=2 B.Fa0 L2 DELIVER sim.arp.requestForMe
      t=2 B.Fa0 L2 CACHE_UPDATE sim.arp.cacheUpdated
      t=2 B.Fa0 L2 GENERATE sim.arp.replySent
      t=2 B.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=2 C.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=2 C.Fa0 L2 DECAPSULATE sim.nic.acceptedBroadcast
      t=2 C.Fa0 L2 DROP sim.arp.notForMe
      t=3 SW.Fa0/2 L2 RECEIVE sim.l2.frameReceived
      t=3 SW.Fa0/2 L2 LEARN sim.switch.learnNew
      t=3 SW.Fa0/2 L2 FORWARD sim.switch.forwardKnown
      t=3 SW.Fa0/1 L2 TRANSMIT sim.l2.frameSent
      t=4 A.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=4 A.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=4 A.Fa0 L2 DELIVER sim.arp.replyReceived
      t=4 A.Fa0 L2 CACHE_UPDATE sim.arp.cacheUpdated
      t=5 A L7 TIMEOUT sim.ping.timeout
      t=6 A L7 GENERATE sim.ping.start
      t=6 A L3 ENCAPSULATE sim.icmp.echoRequestCreated
      t=6 A L3 DECIDE sim.host.destinationLocal
      t=6 A.Fa0 L2 LOOKUP sim.arp.cacheHit
      t=6 A.Fa0 L2 ENCAPSULATE sim.l2.encapsulated
      t=6 A.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=7 SW.Fa0/1 L2 RECEIVE sim.l2.frameReceived
      t=7 SW.Fa0/1 L2 LEARN sim.switch.learnKnown
      t=7 SW.Fa0/1 L2 FORWARD sim.switch.forwardKnown
      t=7 SW.Fa0/2 L2 TRANSMIT sim.l2.frameSent
      t=8 B.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=8 B.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=8 B.Fa0 L3 DELIVER sim.icmp.echoRequestReceived
      t=8 B L3 GENERATE sim.icmp.echoReplyCreated
      t=8 B L3 DECIDE sim.host.destinationLocal
      t=8 B.Fa0 L2 LOOKUP sim.arp.cacheHit
      t=8 B.Fa0 L2 ENCAPSULATE sim.l2.encapsulated
      t=8 B.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=9 SW.Fa0/2 L2 RECEIVE sim.l2.frameReceived
      t=9 SW.Fa0/2 L2 LEARN sim.switch.learnKnown
      t=9 SW.Fa0/2 L2 FORWARD sim.switch.forwardKnown
      t=9 SW.Fa0/1 L2 TRANSMIT sim.l2.frameSent
      t=10 A.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=10 A.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=10 A.Fa0 L3 DELIVER sim.icmp.echoReplyReceived
      t=10 A L7 DELIVER sim.ping.replyReceived
      """;

  private final Topology topology = new Topology();
  private Host a;
  private Host b;
  private Host c;
  private Switch sw;
  private SimulationEngine engine;
  private PingResult result;

  @BeforeEach
  void buildAndPing() {
    a = Lab.pc(topology, "A", "192.168.1.10", null);
    b = Lab.pc(topology, "B", "192.168.1.20", null);
    c = Lab.pc(topology, "C", "192.168.1.30", null);
    sw = topology.addSwitch("SW");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    Lab.cable(topology, c, "Fa0", sw, "Fa0/3");
    engine = new SimulationEngine(topology);
    // Two echo requests: the first is lost while ARP resolves, the second goes through.
    result = engine.ping(a.id(), Lab.ip("192.168.1.20"), 2);
  }

  @Test
  void theEventSequenceIsExactlyThisOne() {
    assertThat(Lab.lines(topology, result.events())).isEqualTo(EXPECTED_TRACE.lines().toList());
  }

  @Test
  void theFirstPingIsLostToArpAndTheSecondSucceeds() {
    assertThat(result.sent()).isEqualTo(2);
    assertThat(result.received()).isEqualTo(1);
    assertThat(result.lost()).isEqualTo(1);
    assertThat(result.reachable()).isTrue();
    assertThat(result.allReceived()).isFalse();

    PingAttempt first = result.attempts().get(0);
    assertThat(first.success()).isFalse();
    assertThat(first.reason()).contains(FailureReason.LOST_TO_ARP);
    assertThat(first.failedAtDeviceId()).isEqualTo(a.id());

    PingAttempt second = result.attempts().get(1);
    assertThat(second.success()).isTrue();
    assertThat(second.repliedFrom()).isEqualTo(Lab.ip("192.168.1.20"));
    // A to switch, switch to B, B to switch, switch to A: four cables, one tick each.
    assertThat(second.roundTripTicks()).isEqualTo(4);
  }

  @Test
  void theArpRequestIsABroadcastFloodedToEveryPortExceptTheIngress() {
    List<SimEvent> requests = transmissionsOf(ArpOperation.REQUEST);

    assertThat(requests)
        .extracting(e -> portOf(e))
        .containsExactly("A.Fa0", "SW.Fa0/2", "SW.Fa0/3");
    assertThat(requests)
        .allSatisfy(
            e -> {
              EthernetFrame frame = e.frame().orElseThrow();
              assertThat(frame.dstMac()).isEqualTo(MacAddress.BROADCAST);
              assertThat(frame.srcMac()).isEqualTo(Lab.mac(a, "Fa0"));
              ArpMessage arp = (ArpMessage) frame.payload();
              assertThat(arp.senderIp()).isEqualTo(Lab.ip("192.168.1.10"));
              assertThat(arp.targetIp()).isEqualTo(Lab.ip("192.168.1.20"));
              assertThat(arp.targetMac()).isEqualTo(ArpMessage.UNKNOWN_MAC);
            });
  }

  @Test
  void theArpReplyIsAUnicastForwardedToOnePort() {
    List<SimEvent> replies = transmissionsOf(ArpOperation.REPLY);

    assertThat(replies).extracting(e -> portOf(e)).containsExactly("B.Fa0", "SW.Fa0/1");
    EthernetFrame frame = replies.get(0).frame().orElseThrow();
    assertThat(frame.srcMac()).isEqualTo(Lab.mac(b, "Fa0"));
    assertThat(frame.dstMac()).isEqualTo(Lab.mac(a, "Fa0"));
    ArpMessage arp = (ArpMessage) frame.payload();
    assertThat(arp.senderMac()).isEqualTo(Lab.mac(b, "Fa0"));
    assertThat(arp.senderIp()).isEqualTo(Lab.ip("192.168.1.20"));
    assertThat(arp.targetMac()).isEqualTo(Lab.mac(a, "Fa0"));
  }

  @Test
  void theEchoRequestAndReplyEachCrossOnlyTheCablesOnTheirPath() {
    List<SimEvent> requests = echoTransmissions(IcmpType.ECHO_REQUEST);
    List<SimEvent> replies = echoTransmissions(IcmpType.ECHO_REPLY);

    assertThat(requests).extracting(e -> portOf(e)).containsExactly("A.Fa0", "SW.Fa0/2");
    assertThat(replies).extracting(e -> portOf(e)).containsExactly("B.Fa0", "SW.Fa0/1");
    EthernetFrame request = requests.get(1).frame().orElseThrow();
    assertThat(request.srcMac()).isEqualTo(Lab.mac(a, "Fa0"));
    assertThat(request.dstMac()).isEqualTo(Lab.mac(b, "Fa0"));
    Ipv4Packet packet = (Ipv4Packet) request.payload();
    assertThat(packet.src()).isEqualTo(Lab.ip("192.168.1.10"));
    assertThat(packet.dst()).isEqualTo(Lab.ip("192.168.1.20"));
    // Same network, so the switch does not touch the TTL.
    assertThat(packet.ttl()).isEqualTo(128);
  }

  @Test
  void theThirdPcSeesOnlyTheBroadcast() {
    List<SimEvent> received =
        result.events().stream()
            .filter(e -> e.deviceId().equals(c.id()) && e.action() == SimAction.RECEIVE)
            .toList();

    assertThat(received).hasSize(1);
    assertThat(received.get(0).frame().orElseThrow().dstMac()).isEqualTo(MacAddress.BROADCAST);
    assertThat(engine.arpTable(c.id())).isEmpty();
  }

  @Test
  void theSwitchLearnedBothMacAddressesButNotTheThirdOne() {
    assertThat(engine.macTable(sw.id()))
        .containsExactly(
            new MacEntry(Lab.mac(a, "Fa0"), "Fa0/1"), new MacEntry(Lab.mac(b, "Fa0"), "Fa0/2"));
  }

  @Test
  void bothSidesCachedEachOther() {
    assertThat(engine.arpTable(a.id()))
        .containsExactly(new ArpEntry(Lab.ip("192.168.1.20"), Lab.mac(b, "Fa0"), "Fa0"));
    assertThat(engine.arpTable(b.id()))
        .containsExactly(new ArpEntry(Lab.ip("192.168.1.10"), Lab.mac(a, "Fa0"), "Fa0"));
  }

  @Test
  void theForwardingDecisionShowsTheAndOperation() {
    SimEvent decision =
        result.events().stream()
            .filter(e -> e.action() == SimAction.DECIDE)
            .findFirst()
            .orElseThrow();

    assertThat(decision.explanationKey()).isEqualTo("sim.host.destinationLocal");
    assertThat(decision.layer()).isEqualTo(3);
    // own IP, mask, own IP AND mask, destination IP, destination IP AND mask
    assertThat(decision.args())
        .containsExactly(
            "192.168.1.10", "255.255.255.0", "192.168.1.0", "192.168.1.20", "192.168.1.0");
  }

  @Test
  void aSecondPingNeedsNoArpAndSucceedsAtOnce() {
    PingResult again = engine.ping(a.id(), Lab.ip("192.168.1.20"), 3);

    assertThat(again.allReceived()).isTrue();
    assertThat(again.events()).noneMatch(e -> e.explanationKey().startsWith("sim.arp.request"));
    assertThat(again.events().get(0).time()).isZero();
  }

  // ---- helpers ----

  private List<SimEvent> transmissionsOf(ArpOperation operation) {
    return result.events().stream()
        .filter(e -> e.action().isTransmission())
        .filter(
            e ->
                e.frame().orElseThrow().payload() instanceof ArpMessage arp
                    && arp.op() == operation)
        .toList();
  }

  private List<SimEvent> echoTransmissions(IcmpType type) {
    return result.events().stream()
        .filter(e -> e.action().isTransmission())
        .filter(
            e ->
                e.frame().orElseThrow().payload() instanceof Ipv4Packet packet
                    && ((IcmpMessage) packet.payload()).type() == type)
        .toList();
  }

  private String portOf(SimEvent event) {
    return topology.device(event.deviceId()).name() + "." + event.portName();
  }
}
