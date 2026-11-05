package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.MacAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The PDU records are immutable, validated value objects. */
class PduTest {

  private static final MacAddress MAC_A = MacAddress.parse("02:00:00:00:00:0a");
  private static final MacAddress MAC_B = MacAddress.parse("02:00:00:00:00:0b");
  private static final Ipv4Address IP_A = Ipv4Address.parse("192.168.1.10");
  private static final Ipv4Address IP_B = Ipv4Address.parse("192.168.1.20");
  private static final IcmpMessage ECHO = new IcmpMessage(IcmpType.ECHO_REQUEST, 0, 1, 1);

  // ---- Ethernet ----

  @Test
  void aFrameCarriesAnIpPacketOrAnArpMessage() {
    Ipv4Packet packet = Ipv4Packet.icmp(IP_A, IP_B, 128, ECHO);
    ArpMessage arp = ArpMessage.request(MAC_A, IP_A, IP_B);

    EthernetFrame ip = EthernetFrame.ipv4(MAC_A, MAC_B, packet);
    EthernetFrame request = EthernetFrame.arp(MAC_A, MacAddress.BROADCAST, arp);

    assertThat(ip.etherType()).isEqualTo(EtherType.IPV4);
    assertThat(ip.payload()).isSameAs(packet);
    assertThat(request.etherType()).isEqualTo(EtherType.ARP);
    assertThat(request.dstMac().isBroadcast()).isTrue();
  }

  @Test
  void theEtherTypeMustMatchThePayload() {
    ArpMessage arp = ArpMessage.request(MAC_A, IP_A, IP_B);
    Ipv4Packet packet = Ipv4Packet.icmp(IP_A, IP_B, 128, ECHO);

    assertThatThrownBy(() -> new EthernetFrame(MAC_A, MAC_B, EtherType.IPV4, arp))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("IPV4");
    assertThatThrownBy(() -> new EthernetFrame(MAC_A, MAC_B, EtherType.ARP, packet))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aSourceAddressCannotBeBroadcastOrMulticast() {
    ArpMessage arp = ArpMessage.request(MAC_A, IP_A, IP_B);

    assertThatThrownBy(() -> EthernetFrame.arp(MacAddress.BROADCAST, MAC_B, arp))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EthernetFrame.arp(MacAddress.parse("01:00:5e:00:00:01"), MAC_B, arp))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aFrameRejectsMissingFields() {
    ArpMessage arp = ArpMessage.request(MAC_A, IP_A, IP_B);
    assertThatThrownBy(() -> new EthernetFrame(null, MAC_B, EtherType.ARP, arp))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new EthernetFrame(MAC_A, null, EtherType.ARP, arp))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new EthernetFrame(MAC_A, MAC_B, null, arp))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new EthernetFrame(MAC_A, MAC_B, EtherType.ARP, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void etherTypesHaveTheirWireValues() {
    assertThat(EtherType.IPV4.value()).isEqualTo(0x0800);
    assertThat(EtherType.ARP.value()).isEqualTo(0x0806);
  }

  // ---- ARP ----

  @Test
  void anArpRequestAsksAboutTheTargetAndLeavesItsMacUnknown() {
    ArpMessage request = ArpMessage.request(MAC_A, IP_A, IP_B);

    assertThat(request.op()).isEqualTo(ArpOperation.REQUEST);
    assertThat(request.senderMac()).isEqualTo(MAC_A);
    assertThat(request.senderIp()).isEqualTo(IP_A);
    assertThat(request.targetIp()).isEqualTo(IP_B);
    assertThat(request.targetMac()).isEqualTo(ArpMessage.UNKNOWN_MAC).isEqualTo(new MacAddress(0));
  }

  @Test
  void anArpReplyAnswersTheRequester() {
    ArpMessage reply = ArpMessage.reply(MAC_B, IP_B, MAC_A, IP_A);

    assertThat(reply.op()).isEqualTo(ArpOperation.REPLY);
    assertThat(reply.senderMac()).isEqualTo(MAC_B);
    assertThat(reply.senderIp()).isEqualTo(IP_B);
    assertThat(reply.targetMac()).isEqualTo(MAC_A);
    assertThat(reply.targetIp()).isEqualTo(IP_A);
  }

  @Test
  void arpOperationsHaveTheirWireValues() {
    assertThat(ArpOperation.REQUEST.value()).isEqualTo(1);
    assertThat(ArpOperation.REPLY.value()).isEqualTo(2);
  }

  @Test
  void anArpMessageRejectsMissingFields() {
    assertThatThrownBy(() -> new ArpMessage(null, MAC_A, IP_A, MAC_B, IP_B))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ArpMessage(ArpOperation.REPLY, null, IP_A, MAC_B, IP_B))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ArpMessage(ArpOperation.REPLY, MAC_A, null, MAC_B, IP_B))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ArpMessage(ArpOperation.REPLY, MAC_A, IP_A, null, IP_B))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ArpMessage(ArpOperation.REPLY, MAC_A, IP_A, MAC_B, null))
        .isInstanceOf(NullPointerException.class);
  }

  // ---- IPv4 ----

  @Test
  void aPacketKeepsItsAddressesWhenTheTtlChanges() {
    Ipv4Packet packet = Ipv4Packet.icmp(IP_A, IP_B, 128, ECHO);

    Ipv4Packet forwarded = packet.withTtl(127);

    assertThat(forwarded.ttl()).isEqualTo(127);
    assertThat(forwarded.src()).isEqualTo(IP_A);
    assertThat(forwarded.dst()).isEqualTo(IP_B);
    assertThat(forwarded.protocol()).isEqualTo(IpProtocol.ICMP);
    assertThat(forwarded.payload()).isSameAs(ECHO);
    assertThat(packet.ttl()).isEqualTo(128); // the original is unchanged
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 256, 1000})
  void theTtlMustFitInEightBits(int ttl) {
    assertThatThrownBy(() -> Ipv4Packet.icmp(IP_A, IP_B, ttl, ECHO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 255})
  void theTtlBoundsAreAccepted(int ttl) {
    assertThat(Ipv4Packet.icmp(IP_A, IP_B, ttl, ECHO).ttl()).isEqualTo(ttl);
  }

  @Test
  void theProtocolMustMatchThePayload() {
    ArpMessage notIcmp = ArpMessage.request(MAC_A, IP_A, IP_B);

    assertThatThrownBy(() -> new Ipv4Packet(IP_A, IP_B, 64, IpProtocol.ICMP, notIcmp))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(IpProtocol.ICMP.value()).isEqualTo(1);
  }

  @Test
  void aPacketRejectsMissingFields() {
    assertThatThrownBy(() -> Ipv4Packet.icmp(null, IP_B, 64, ECHO))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Ipv4Packet.icmp(IP_A, null, 64, ECHO))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Ipv4Packet(IP_A, IP_B, 64, null, ECHO))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new Ipv4Packet(IP_A, IP_B, 64, IpProtocol.ICMP, null))
        .isInstanceOf(NullPointerException.class);
  }

  // ---- ICMP ----

  @Test
  void icmpTypesHaveTheirRfc792Numbers() {
    assertThat(IcmpType.ECHO_REPLY.number()).isEqualTo(0);
    assertThat(IcmpType.DESTINATION_UNREACHABLE.number()).isEqualTo(3);
    assertThat(IcmpType.ECHO_REQUEST.number()).isEqualTo(8);
    assertThat(IcmpType.TIME_EXCEEDED.number()).isEqualTo(11);
  }

  @Test
  void onlyTheTwoErrorTypesAreErrors() {
    assertThat(IcmpType.DESTINATION_UNREACHABLE.isError()).isTrue();
    assertThat(IcmpType.TIME_EXCEEDED.isError()).isTrue();
    assertThat(IcmpType.ECHO_REQUEST.isError()).isFalse();
    assertThat(IcmpType.ECHO_REPLY.isError()).isFalse();
  }

  @Test
  void anIcmpMessageChecksItsNumbers() {
    assertThatThrownBy(() -> new IcmpMessage(null, 0, 1, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new IcmpMessage(IcmpType.ECHO_REQUEST, -1, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IcmpMessage(IcmpType.ECHO_REQUEST, 256, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IcmpMessage(IcmpType.ECHO_REQUEST, 0, -1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IcmpMessage(IcmpType.ECHO_REQUEST, 0, 65_536, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IcmpMessage(IcmpType.ECHO_REQUEST, 0, 1, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IcmpMessage(IcmpType.ECHO_REQUEST, 0, 1, 65_536))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new IcmpMessage(IcmpType.ECHO_REPLY, 255, 65_535, 65_535).seq()).isEqualTo(65_535);
  }

  @Test
  void pdusAreValueObjects() {
    assertThat(new IcmpMessage(IcmpType.ECHO_REQUEST, 0, 1, 1))
        .isEqualTo(ECHO)
        .hasSameHashCodeAs(ECHO);
    assertThat(ArpMessage.request(MAC_A, IP_A, IP_B))
        .isEqualTo(ArpMessage.request(MAC_A, IP_A, IP_B));
  }
}
