package io.github.mrramez.netlabstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import io.github.mrramez.netlabstudio.core.net.Ipv4Network;
import io.github.mrramez.netlabstudio.core.net.SubnetMask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PortAndDeviceTest {

  private final Topology topology = new Topology();

  private static Ipv4Address ip(String text) {
    return Ipv4Address.parse(text);
  }

  private static SubnetMask mask(String text) {
    return SubnetMask.parse(text);
  }

  // ---- IPv4 configuration --------------------------------------------------------------------

  @Test
  void aPcNicTakesAnAddressAndReportsItsNetwork() {
    Pc pc = topology.addPc("PC");
    assertThat(pc.nic().hasIpv4()).isFalse();
    assertThat(pc.nic().network()).isEmpty();

    pc.nic().setIpv4(ip("192.168.1.10"), mask("/24"));

    assertThat(pc.nic().ip()).contains(ip("192.168.1.10"));
    assertThat(pc.nic().mask()).contains(mask("255.255.255.0"));
    assertThat(pc.nic().network()).contains(Ipv4Network.parse("192.168.1.0/24"));
    pc.nic().clearIpv4();
    assertThat(pc.nic().hasIpv4()).isFalse();
    assertThat(pc.nic().ip()).isEmpty();
  }

  @ParameterizedTest(name = "{0}{1} is not a host address")
  @CsvSource({
    "224.0.0.1, /24",
    "240.0.0.1, /24",
    "255.255.255.255, /24",
    "127.0.0.1, /8",
    "0.1.2.3, /24",
    "192.168.1.0, /24",
    "192.168.1.255, /24",
    "10.0.0.0, /8",
    "10.255.255.255, /8",
    "192.168.1.0, /30",
  })
  void refusesAddressesThatCannotBelongToAHost(String address, String prefix) {
    Pc pc = topology.addPc("PC");

    assertThatThrownBy(() -> pc.nic().setIpv4(ip(address), mask(prefix)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(pc.nic().hasIpv4()).isFalse();
  }

  @ParameterizedTest(name = "{0}{1} is accepted")
  @CsvSource({
    "192.168.1.1, /24",
    "192.168.1.254, /24",
    "10.0.0.1, /8",
    "192.168.1.0, /31",
    "192.168.1.1, /31",
    "192.168.1.7, /32",
    "172.16.5.9, /12"
  })
  void acceptsOrdinaryHostAddressesAndThePointToPointPrefixes(String address, String prefix) {
    Pc pc = topology.addPc("PC");

    pc.nic().setIpv4(ip(address), mask(prefix));

    assertThat(pc.nic().ip()).contains(ip(address));
  }

  @Test
  void onlyHostsAndRoutersHaveIpAddresses() {
    Switch sw = topology.addSwitch("SW");
    Hub hub = topology.addHub("H");
    Pc pc = topology.addPc("PC");
    Router router = topology.addRouter("R");

    assertThatThrownBy(() -> sw.port("Fa0/1").setIpv4(ip("10.0.0.1"), mask("/8")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> hub.port("Port1").setIpv4(ip("10.0.0.1"), mask("/8")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> pc.port("RS232").setIpv4(ip("10.0.0.1"), mask("/8")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> router.port("Console").setIpv4(ip("10.0.0.1"), mask("/8")))
        .isInstanceOf(IllegalStateException.class);
    router.port("G0/0").setIpv4(ip("10.0.0.1"), mask("/8"));
    assertThat(router.port("G0/0").hasIpv4()).isTrue();
    assertThat(sw.port("Fa0/1").canHaveIpv4()).isFalse();
  }

  @Test
  void setIpv4RejectsNulls() {
    Pc pc = topology.addPc("PC");
    assertThatThrownBy(() -> pc.nic().setIpv4(null, mask("/24")))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> pc.nic().setIpv4(ip("10.0.0.1"), null))
        .isInstanceOf(NullPointerException.class);
  }

  // ---- ports ---------------------------------------------------------------------------------

  @Test
  void portIdsAndLabelsIdentifyTheDevice() {
    Pc pc = (Pc) topology.addDevice(DeviceType.PC, "pc-7", "Office PC");

    assertThat(pc.nic().id()).isEqualTo("pc-7/Fa0");
    assertThat(pc.nic().label()).isEqualTo("Office PC Fa0");
    assertThat(pc.nic().toString()).isEqualTo("pc-7/Fa0");
    assertThat(pc.nic().device()).isSameAs(pc);
  }

  @Test
  void adminStateAndDuplexCanBeChanged() {
    Switch sw = topology.addSwitch("SW");
    Port port = sw.port("Fa0/1");
    assertThat(port.duplex()).isEqualTo(Duplex.AUTO);

    port.setDuplex(Duplex.FULL);
    port.setAdminUp(false);

    assertThat(port.duplex()).isEqualTo(Duplex.FULL);
    assertThat(port.isAdminUp()).isFalse();
    assertThatThrownBy(() -> port.setDuplex(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> sw.port("Console").setDuplex(Duplex.HALF))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anUnconnectedPortIsDown() {
    Pc pc = topology.addPc("PC");

    assertThat(pc.nic().linkState()).isEqualTo(LinkState.DOWN);
    assertThat(pc.nic().link()).isEmpty();
  }

  // ---- devices -------------------------------------------------------------------------------

  @Test
  void aDeviceCanBeRenamedAndMoved() {
    Pc pc = topology.addPc("PC0");
    assertThat(pc.x()).isZero();
    assertThat(pc.y()).isZero();

    pc.setName("Reception");
    pc.setPosition(120.5, -40.25);

    assertThat(pc.name()).isEqualTo("Reception");
    assertThat(pc.x()).isEqualTo(120.5);
    assertThat(pc.y()).isEqualTo(-40.25);
    assertThat(pc.toString()).contains("Reception").contains("PC");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void refusesABlankName(String name) {
    Pc pc = topology.addPc("PC0");
    assertThatThrownBy(() -> pc.setName(name)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> topology.addPc(name)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesAnOverlongNameAndNull() {
    Pc pc = topology.addPc("PC0");
    assertThatThrownBy(() -> pc.setName("x".repeat(65)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> pc.setName(null)).isInstanceOf(NullPointerException.class);
    pc.setName("x".repeat(64));
    assertThat(pc.name()).hasSize(64);
  }

  @Test
  void anArabicNameIsFine() {
    Pc pc = topology.addPc("حاسوب ١");
    assertThat(pc.name()).isEqualTo("حاسوب ١");
  }

  @Test
  void refusesAPositionThatIsNotANumber() {
    Pc pc = topology.addPc("PC0");
    assertThatThrownBy(() -> pc.setPosition(Double.NaN, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> pc.setPosition(0, Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findsPortsByExactName() {
    Switch sw = topology.addSwitch("SW");

    assertThat(sw.findPort("Fa0/3")).containsSame(sw.ports().get(2));
    assertThat(sw.findPort("fa0/3")).isEmpty();
    assertThatThrownBy(() -> sw.port("Fa0/99"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Fa0/99");
  }

  @Test
  void theListOfPortsCannotBeModified() {
    Pc pc = topology.addPc("PC0");
    assertThatThrownBy(() -> pc.ports().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  // ---- hosts ---------------------------------------------------------------------------------

  @Test
  void aHostKeepsItsDefaultGateway() {
    Server server = topology.addServer("Web");
    assertThat(server.defaultGateway()).isEmpty();

    server.configure(ip("192.168.1.10"), mask("/24"), ip("192.168.1.1"));

    assertThat(server.nic().ip()).contains(ip("192.168.1.10"));
    assertThat(server.defaultGateway()).contains(ip("192.168.1.1"));
    server.configure(ip("192.168.1.11"), mask("/24"));
    assertThat(server.defaultGateway()).isEmpty();
    server.setDefaultGateway(ip("10.0.0.1"));
    assertThat(server.defaultGateway()).contains(ip("10.0.0.1"));
    server.clearDefaultGateway();
    assertThat(server.defaultGateway()).isEmpty();
    assertThatThrownBy(() -> server.setDefaultGateway(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void deviceTypesKnowTheirLayerAndPinOut() {
    assertThat(DeviceType.HUB.osiLayer()).isEqualTo(1);
    assertThat(DeviceType.SWITCH.osiLayer()).isEqualTo(2);
    assertThat(DeviceType.ROUTER.osiLayer()).isEqualTo(3);
    assertThat(DeviceType.PC.osiLayer()).isEqualTo(7);
    assertThat(DeviceType.PC.isHost()).isTrue();
    assertThat(DeviceType.SERVER.isHost()).isTrue();
    assertThat(DeviceType.ROUTER.isHost()).isFalse();
    assertThat(DeviceType.ROUTER.mdi()).isEqualTo(MdiType.MDI);
    assertThat(DeviceType.BRIDGE.mdi()).isEqualTo(MdiType.MDI_X);
  }

  @Test
  void portTypesKnowWhetherTheyAreEthernet() {
    assertThat(PortType.FAST_ETHERNET.isEthernet()).isTrue();
    assertThat(PortType.GIGABIT_ETHERNET.isEthernet()).isTrue();
    assertThat(PortType.SERIAL.isEthernet()).isFalse();
    assertThat(PortType.CONSOLE.isEthernet()).isFalse();
    assertThat(PortType.RS232.isEthernet()).isFalse();
    assertThat(CableType.STRAIGHT.isEthernet()).isTrue();
    assertThat(CableType.CROSSOVER.isEthernet()).isTrue();
    assertThat(CableType.SERIAL.isEthernet()).isFalse();
    assertThat(CableType.CONSOLE.isEthernet()).isFalse();
  }
}
