package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.model.Topology;
import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Frames that a normal ping never produces, fed straight to a device. */
class DeviceBehaviourEdgeCasesTest {

  private final Topology topology = new Topology();
  private Host a;
  private Host b;
  private Router router;
  private Simulation simulation;

  @BeforeEach
  void build() {
    a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.1");
    b = Lab.pc(topology, "B", "192.168.1.20", "192.168.1.1");
    router = topology.addRouter("R1");
    Lab.configure(router, "G0/0", "192.168.1.1", "/24");
    var sw = topology.addSwitch("SW");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    Lab.cable(topology, router, "G0/0", sw, "Fa0/3");
    simulation = new SimulationEngine(topology).newSimulation();
  }

  private static Ipv4Packet icmp(String src, String dst, IcmpType type, int code) {
    return Ipv4Packet.icmp(Lab.ip(src), Lab.ip(dst), 64, new IcmpMessage(type, code, 1, 1));
  }

  private List<String> keysAfterInjectingInto(
      io.github.mrramez.netlabstudio.core.model.Port port, EthernetFrame frame) {
    return simulation.inject(port, frame).stream().map(SimEvent::explanationKey).toList();
  }

  @Test
  void aHostIgnoresAnEchoReplyItNeverAskedFor() {
    Ipv4Packet reply = icmp("192.168.1.20", "192.168.1.10", IcmpType.ECHO_REPLY, 0);
    EthernetFrame frame = EthernetFrame.ipv4(Lab.mac(b, "Fa0"), Lab.mac(a, "Fa0"), reply);

    assertThat(keysAfterInjectingInto(a.nic(), frame))
        .containsExactly("sim.l2.frameReceived", "sim.nic.acceptedUnicast", "sim.icmp.unexpected");
  }

  @Test
  void aHostIgnoresAnIcmpErrorItNeverAskedFor() {
    Ipv4Packet error = icmp("192.168.1.1", "192.168.1.10", IcmpType.TIME_EXCEEDED, 0);
    EthernetFrame frame = EthernetFrame.ipv4(Lab.mac(router, "G0/0"), Lab.mac(a, "Fa0"), error);

    assertThat(keysAfterInjectingInto(a.nic(), frame)).endsWith("sim.icmp.unexpected");
  }

  @Test
  void aNicIgnoresMulticastFramesItHasNotJoined() {
    ArpMessage arp =
        ArpMessage.request(Lab.mac(b, "Fa0"), Lab.ip("192.168.1.20"), Lab.ip("192.168.1.10"));
    EthernetFrame frame =
        EthernetFrame.arp(Lab.mac(b, "Fa0"), MacAddress.parse("01:00:5e:00:00:fb"), arp);

    assertThat(keysAfterInjectingInto(a.nic(), frame))
        .containsExactly("sim.l2.frameReceived", "sim.nic.frameNotForMe");
  }

  @Test
  void aHostIgnoresAnArpReplyThatIsMeantForSomeoneElse() {
    ArpMessage reply =
        ArpMessage.reply(
            Lab.mac(b, "Fa0"),
            Lab.ip("192.168.1.20"),
            Lab.mac(router, "G0/0"),
            Lab.ip("192.168.1.1"));
    EthernetFrame frame = EthernetFrame.arp(Lab.mac(b, "Fa0"), MacAddress.BROADCAST, reply);

    assertThat(keysAfterInjectingInto(a.nic(), frame)).endsWith("sim.arp.notForMe");
    assertThat(simulationArpEntries(a)).isEmpty();
  }

  @Test
  void aHostWithoutAnAddressDropsPacketsForAnyone() {
    a.nic().clearIpv4();
    Ipv4Packet packet = icmp("192.168.1.20", "192.168.1.10", IcmpType.ECHO_REQUEST, 0);
    EthernetFrame frame = EthernetFrame.ipv4(Lab.mac(b, "Fa0"), MacAddress.BROADCAST, packet);

    List<SimEvent> events = simulation.inject(a.nic(), frame);

    SimEvent drop = events.get(events.size() - 1);
    assertThat(drop.explanationKey()).isEqualTo("sim.host.packetNotForMe");
    assertThat(drop.args()).containsExactly("192.168.1.10", "none");
  }

  @Test
  void aRouterStopsAnIpPacketSentToTheBroadcastMacForAnotherAddress() {
    Ipv4Packet packet = icmp("192.168.1.20", "10.1.1.1", IcmpType.ECHO_REQUEST, 0);
    EthernetFrame frame = EthernetFrame.ipv4(Lab.mac(b, "Fa0"), MacAddress.BROADCAST, packet);

    assertThat(keysAfterInjectingInto(router.port("G0/0"), frame))
        .containsExactly(
            "sim.l2.frameReceived",
            "sim.nic.acceptedBroadcast",
            "sim.router.broadcastNotForwarded");
  }

  @Test
  void aRouterRepliesToAPingSentToTheBroadcastMacForItsOwnAddress() {
    Ipv4Packet packet = icmp("192.168.1.20", "192.168.1.1", IcmpType.ECHO_REQUEST, 0);
    EthernetFrame frame = EthernetFrame.ipv4(Lab.mac(b, "Fa0"), MacAddress.BROADCAST, packet);

    assertThat(keysAfterInjectingInto(router.port("G0/0"), frame))
        .contains("sim.icmp.echoReplyCreated");
  }

  @Test
  void aRouterIgnoresAnIcmpMessageThatIsNotAnEchoRequest() {
    Ipv4Packet packet = icmp("192.168.1.20", "192.168.1.1", IcmpType.ECHO_REPLY, 0);
    EthernetFrame frame = EthernetFrame.ipv4(Lab.mac(b, "Fa0"), Lab.mac(router, "G0/0"), packet);

    assertThat(keysAfterInjectingInto(router.port("G0/0"), frame)).endsWith("sim.icmp.unexpected");
  }

  @Test
  void aRouterNeverAnswersAnIcmpErrorWithAnotherError() {
    // An error message that expires in transit must not cause a second error (no ping-pong).
    Ipv4Packet error =
        Ipv4Packet.icmp(
            Lab.ip("192.168.1.20"),
            Lab.ip("10.1.1.1"),
            1,
            new IcmpMessage(IcmpType.DESTINATION_UNREACHABLE, 1, 1, 1));
    EthernetFrame frame = EthernetFrame.ipv4(Lab.mac(b, "Fa0"), Lab.mac(router, "G0/0"), error);

    List<String> keys = keysAfterInjectingInto(router.port("G0/0"), frame);

    assertThat(keys).contains("sim.router.ttlExpired");
    assertThat(keys).doesNotContain("sim.icmp.timeExceededCreated");
  }

  @Test
  void aRouterThatHasNoRouteBackToTheSenderDropsItsOwnReply() {
    // The sender's address is on no connected network, so the router cannot answer.
    Ipv4Packet packet = icmp("172.16.0.9", "192.168.1.1", IcmpType.ECHO_REQUEST, 0);
    EthernetFrame frame = EthernetFrame.ipv4(Lab.mac(b, "Fa0"), Lab.mac(router, "G0/0"), packet);

    assertThat(keysAfterInjectingInto(router.port("G0/0"), frame))
        .endsWith("sim.router.noRouteToSender");
  }

  @Test
  void aRouterDoesNotAnswerArpForAnAddressThatIsNotItsOwn() {
    ArpMessage arp =
        ArpMessage.request(Lab.mac(b, "Fa0"), Lab.ip("192.168.1.20"), Lab.ip("192.168.1.77"));
    EthernetFrame frame = EthernetFrame.arp(Lab.mac(b, "Fa0"), MacAddress.BROADCAST, arp);

    assertThat(keysAfterInjectingInto(router.port("G0/0"), frame))
        .endsWith("sim.router.broadcastNotForwarded");
  }

  @Test
  void anArpReplyThatNobodyWaitsForStillFillsTheCache() {
    ArpMessage reply =
        ArpMessage.reply(
            Lab.mac(b, "Fa0"), Lab.ip("192.168.1.20"), Lab.mac(a, "Fa0"), Lab.ip("192.168.1.10"));
    EthernetFrame frame = EthernetFrame.arp(Lab.mac(b, "Fa0"), Lab.mac(a, "Fa0"), reply);

    simulation.inject(a.nic(), frame);

    assertThat(simulationArpEntries(a))
        .extracting(ArpEntry::ip)
        .containsExactly(Ipv4Address.parse("192.168.1.20"));
  }

  private List<ArpEntry> simulationArpEntries(Host host) {
    return List.copyOf(simulation.state(host).arp.values());
  }
}
