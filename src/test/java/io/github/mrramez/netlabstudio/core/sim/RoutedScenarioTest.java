package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.model.Topology;
import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.Ipv4Network;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CURRICULUM §4.5: PC A (192.168.1.10, gateway .1) - R1 (G0/0 192.168.1.1, G0/1 192.168.2.1) - PC B
 * (192.168.2.10, gateway 192.168.2.1).
 */
class RoutedScenarioTest {

  private static final String EXPECTED_TRACE =
      """
      t=0 A L7 GENERATE sim.ping.start
      t=0 A L3 ENCAPSULATE sim.icmp.echoRequestCreated
      t=0 A L3 DECIDE sim.host.destinationRemote
      t=0 A.Fa0 L2 LOOKUP sim.arp.cacheMiss
      t=0 A.Fa0 L3 DROP sim.arp.firstPacketLost
      t=0 A.Fa0 L2 GENERATE sim.arp.requestSent
      t=0 A.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=1 R1.G0/0 L2 RECEIVE sim.l2.frameReceived
      t=1 R1.G0/0 L2 DECAPSULATE sim.nic.acceptedBroadcast
      t=1 R1.G0/0 L2 DELIVER sim.arp.requestForMe
      t=1 R1.G0/0 L2 CACHE_UPDATE sim.arp.cacheUpdated
      t=1 R1.G0/0 L2 GENERATE sim.arp.replySent
      t=1 R1.G0/0 L2 TRANSMIT sim.l2.frameSent
      t=2 A.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=2 A.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=2 A.Fa0 L2 DELIVER sim.arp.replyReceived
      t=2 A.Fa0 L2 CACHE_UPDATE sim.arp.cacheUpdated
      t=3 A L7 TIMEOUT sim.ping.timeout
      t=4 A L7 GENERATE sim.ping.start
      t=4 A L3 ENCAPSULATE sim.icmp.echoRequestCreated
      t=4 A L3 DECIDE sim.host.destinationRemote
      t=4 A.Fa0 L2 LOOKUP sim.arp.cacheHit
      t=4 A.Fa0 L2 ENCAPSULATE sim.l2.encapsulated
      t=4 A.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=5 R1.G0/0 L2 RECEIVE sim.l2.frameReceived
      t=5 R1.G0/0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=5 R1.G0/0 L3 DECREMENT_TTL sim.router.ttlDecrement
      t=5 R1.G0/1 L3 LOOKUP sim.router.routeFound
      t=5 R1.G0/1 L2 LOOKUP sim.arp.cacheMiss
      t=5 R1.G0/1 L2 QUEUE sim.arp.packetHeld
      t=5 R1.G0/1 L2 GENERATE sim.arp.requestSent
      t=5 R1.G0/1 L2 TRANSMIT sim.l2.frameSent
      t=6 B.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=6 B.Fa0 L2 DECAPSULATE sim.nic.acceptedBroadcast
      t=6 B.Fa0 L2 DELIVER sim.arp.requestForMe
      t=6 B.Fa0 L2 CACHE_UPDATE sim.arp.cacheUpdated
      t=6 B.Fa0 L2 GENERATE sim.arp.replySent
      t=6 B.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=7 R1.G0/1 L2 RECEIVE sim.l2.frameReceived
      t=7 R1.G0/1 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=7 R1.G0/1 L2 DELIVER sim.arp.replyReceived
      t=7 R1.G0/1 L2 CACHE_UPDATE sim.arp.cacheUpdated
      t=7 R1.G0/1 L2 RELEASE sim.arp.packetReleased
      t=7 R1.G0/1 L2 REWRITE sim.router.rewriteFrame
      t=7 R1.G0/1 L2 TRANSMIT sim.l2.frameSent
      t=8 B.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=8 B.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=8 B.Fa0 L3 DELIVER sim.icmp.echoRequestReceived
      t=8 B L3 GENERATE sim.icmp.echoReplyCreated
      t=8 B L3 DECIDE sim.host.destinationRemote
      t=8 B.Fa0 L2 LOOKUP sim.arp.cacheHit
      t=8 B.Fa0 L2 ENCAPSULATE sim.l2.encapsulated
      t=8 B.Fa0 L2 TRANSMIT sim.l2.frameSent
      t=9 R1.G0/1 L2 RECEIVE sim.l2.frameReceived
      t=9 R1.G0/1 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=9 R1.G0/1 L3 DECREMENT_TTL sim.router.ttlDecrement
      t=9 R1.G0/0 L3 LOOKUP sim.router.routeFound
      t=9 R1.G0/0 L2 LOOKUP sim.arp.cacheHit
      t=9 R1.G0/0 L2 REWRITE sim.router.rewriteFrame
      t=9 R1.G0/0 L2 TRANSMIT sim.l2.frameSent
      t=10 A.Fa0 L2 RECEIVE sim.l2.frameReceived
      t=10 A.Fa0 L2 DECAPSULATE sim.nic.acceptedUnicast
      t=10 A.Fa0 L3 DELIVER sim.icmp.echoReplyReceived
      t=10 A L7 DELIVER sim.ping.replyReceived
      """;

  private final Topology topology = new Topology();
  private Host a;
  private Host b;
  private Router r1;
  private SimulationEngine engine;
  private PingResult result;

  @BeforeEach
  void buildAndPing() {
    a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.1");
    b = topology.addPc("B");
    b.configure(Lab.ip("192.168.2.10"), Lab.mask("/24"), Lab.ip("192.168.2.1"));
    r1 = topology.addRouter("R1");
    Lab.configure(r1, "G0/0", "192.168.1.1", "/24");
    Lab.configure(r1, "G0/1", "192.168.2.1", "/24");
    Lab.cable(topology, a, "Fa0", r1, "G0/0");
    Lab.cable(topology, b, "Fa0", r1, "G0/1");
    engine = new SimulationEngine(topology);
    result = engine.ping(a.id(), Lab.ip("192.168.2.10"), 2);
  }

  @Test
  void theEventSequenceIsExactlyThisOne() {
    assertThat(Lab.lines(topology, result.events())).isEqualTo(EXPECTED_TRACE.lines().toList());
  }

  @Test
  void theCrossNetworkPingSucceedsOnceArpIsDoneOnBothLans() {
    PingAttempt first = result.attempts().get(0);
    PingAttempt second = result.attempts().get(1);

    assertThat(first.reason()).contains(FailureReason.LOST_TO_ARP);
    assertThat(second.success()).isTrue();
    assertThat(second.repliedFrom()).isEqualTo(Lab.ip("192.168.2.10"));
    // A - R1 - B and back: 4 cables, plus 2 for the ARP exchange the router needs on the way.
    assertThat(second.roundTripTicks()).isEqualTo(6);
  }

  @Test
  void theHostSendsToTheGatewayAndShowsTheAndOperationThatDecidedIt() {
    SimEvent decision =
        result.events().stream()
            .filter(e -> e.action() == SimAction.DECIDE)
            .findFirst()
            .orElseThrow();

    assertThat(decision.explanationKey()).isEqualTo("sim.host.destinationRemote");
    // own IP, mask, own network, destination IP, destination network, gateway
    assertThat(decision.args())
        .containsExactly(
            "192.168.1.10",
            "255.255.255.0",
            "192.168.1.0",
            "192.168.2.10",
            "192.168.2.0",
            "192.168.1.1");
    SimEvent firstFrame = echoRequestFrames().get(0);
    // The frame goes to the router's MAC address, not to B's, although the IP packet is for B.
    assertThat(firstFrame.frame().orElseThrow().dstMac()).isEqualTo(Lab.mac(r1, "G0/0"));
  }

  @Test
  void atTheRouterTheMacAddressesChangeButTheIpAddressesDoNot() {
    List<SimEvent> requests = echoRequestFrames();
    assertThat(requests).hasSize(2);
    EthernetFrame beforeRouter = requests.get(0).frame().orElseThrow();
    EthernetFrame afterRouter = requests.get(1).frame().orElseThrow();

    assertThat(beforeRouter.srcMac()).isEqualTo(Lab.mac(a, "Fa0"));
    assertThat(beforeRouter.dstMac()).isEqualTo(Lab.mac(r1, "G0/0"));
    assertThat(afterRouter.srcMac()).isEqualTo(Lab.mac(r1, "G0/1"));
    assertThat(afterRouter.dstMac()).isEqualTo(Lab.mac(b, "Fa0"));

    Ipv4Packet packetBefore = (Ipv4Packet) beforeRouter.payload();
    Ipv4Packet packetAfter = (Ipv4Packet) afterRouter.payload();
    assertThat(packetBefore.src()).isEqualTo(Lab.ip("192.168.1.10")).isEqualTo(packetAfter.src());
    assertThat(packetBefore.dst()).isEqualTo(Lab.ip("192.168.2.10")).isEqualTo(packetAfter.dst());
  }

  @Test
  void theTtlDropsByExactlyOneAtTheRouter() {
    List<SimEvent> requests = echoRequestFrames();

    assertThat(((Ipv4Packet) requests.get(0).frame().orElseThrow().payload()).ttl()).isEqualTo(128);
    assertThat(((Ipv4Packet) requests.get(1).frame().orElseThrow().payload()).ttl()).isEqualTo(127);
    SimEvent decrement =
        result.events().stream()
            .filter(e -> e.action() == SimAction.DECREMENT_TTL)
            .findFirst()
            .orElseThrow();
    assertThat(decrement.args()).containsExactly(128, 127);
    // The reply made the same trip in the other direction, starting at B's own TTL.
    SimEvent received =
        result.events().stream()
            .filter(e -> e.explanationKey().equals("sim.icmp.echoReplyReceived"))
            .findFirst()
            .orElseThrow();
    assertThat(received.args()).containsExactly("192.168.2.10", 2, 127);
  }

  @Test
  void theRewriteEventReportsOldAndNewMacAddresses() {
    SimEvent rewrite =
        result.events().stream()
            .filter(e -> e.action() == SimAction.REWRITE)
            .findFirst()
            .orElseThrow();

    assertThat(rewrite.deviceId()).isEqualTo(r1.id());
    assertThat(rewrite.portName()).isEqualTo("G0/1");
    assertThat(rewrite.args())
        .containsExactly(
            Lab.mac(a, "Fa0").toString(),
            Lab.mac(r1, "G0/0").toString(),
            Lab.mac(r1, "G0/1").toString(),
            Lab.mac(b, "Fa0").toString());
  }

  @Test
  void theRouterDoesNotForwardBroadcastsSoEachArpStaysOnItsOwnLan() {
    List<SimEvent> broadcasts =
        result.events().stream()
            .filter(e -> e.action() == SimAction.TRANSMIT)
            .filter(e -> e.frame().orElseThrow().dstMac().isBroadcast())
            .toList();

    // Exactly two broadcasts: A's request on LAN 1 and the router's own request on LAN 2.
    assertThat(broadcasts).hasSize(2);
    assertThat(broadcasts).extracting(SimEvent::deviceId).containsExactly(a.id(), r1.id());
    assertThat(broadcasts.get(0).portName()).isEqualTo("Fa0");
    assertThat(broadcasts.get(1).portName()).isEqualTo("G0/1");
    // B never saw A's request (its sender IP is not even on B's network).
    List<ArpMessage> seenByB =
        result.events().stream()
            .filter(e -> e.deviceId().equals(b.id()) && e.action() == SimAction.RECEIVE)
            .map(e -> e.frame().orElseThrow().payload())
            .filter(ArpMessage.class::isInstance)
            .map(ArpMessage.class::cast)
            .toList();
    assertThat(seenByB).isNotEmpty();
    assertThat(seenByB).extracting(ArpMessage::senderIp).doesNotContain(Lab.ip("192.168.1.10"));
  }

  @Test
  void theRouterHasItsOwnArpTableWithOneEntryPerLan() {
    assertThat(engine.arpTable(r1.id()))
        .containsExactly(
            new ArpEntry(Lab.ip("192.168.1.10"), Lab.mac(a, "Fa0"), "G0/0"),
            new ArpEntry(Lab.ip("192.168.2.10"), Lab.mac(b, "Fa0"), "G0/1"));
    assertThat(engine.arpTable(a.id()))
        .containsExactly(new ArpEntry(Lab.ip("192.168.1.1"), Lab.mac(r1, "G0/0"), "Fa0"));
    // B learned the router from its ARP request and never had to ask.
    assertThat(engine.arpTable(b.id()))
        .containsExactly(new ArpEntry(Lab.ip("192.168.2.1"), Lab.mac(r1, "G0/1"), "Fa0"));
  }

  @Test
  void connectedRoutesAppearOnceTheInterfacesAreUp() {
    assertThat(engine.routingTable(r1.id()))
        .containsExactly(
            new Route(Ipv4Network.parse("192.168.1.0/24"), "G0/0"),
            new Route(Ipv4Network.parse("192.168.2.0/24"), "G0/1"));
  }

  @Test
  void pingingTheRoutersOwnAddressesWorks() {
    PingResult near = engine.ping(a.id(), Lab.ip("192.168.1.1"), 1);
    PingResult far = engine.ping(a.id(), Lab.ip("192.168.2.1"), 1);

    assertThat(near.allReceived()).isTrue();
    assertThat(far.allReceived()).isTrue();
    // The reply from a router starts at TTL 255; it has not crossed another router.
    assertThat(far.events())
        .filteredOn(e -> e.explanationKey().equals("sim.icmp.echoReplyReceived"))
        .extracting(SimEvent::args)
        .containsExactly(List.<Object>of("192.168.2.1", 1, 255));
    assertThat(far.attempts().get(0).repliedFrom()).isEqualTo(Ipv4Address.parse("192.168.2.1"));
  }

  @Test
  void aSwitchedOffRouterCannotBePingedByAnotherInterfaceAddress() {
    r1.port("G0/1").setAdminUp(false);

    PingResult far = engine.ping(a.id(), Lab.ip("192.168.2.1"), 1);

    // The address belongs to an interface that is down, so the router does not own it any more.
    assertThat(far.reachable()).isFalse();
  }

  private List<SimEvent> echoRequestFrames() {
    return result.events().stream()
        .filter(e -> e.action() == SimAction.TRANSMIT)
        .filter(
            e ->
                e.frame().orElseThrow().payload() instanceof Ipv4Packet packet
                    && ((IcmpMessage) packet.payload()).type() == IcmpType.ECHO_REQUEST)
        .toList();
  }
}
