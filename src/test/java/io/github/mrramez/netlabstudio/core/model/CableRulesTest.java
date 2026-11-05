package io.github.mrramez.netlabstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Which cable works between which devices (SPEC §5, "Cables"). */
class CableRulesTest {

  private final Topology topology = new Topology();

  private Port ethernet(DeviceType type) {
    Device device = topology.addDevice(type, type.name() + topology.devices().size());
    return device.ports().stream()
        .filter(port -> port.type().isEthernet())
        .findFirst()
        .orElseThrow();
  }

  @ParameterizedTest(name = "{0} - {1} needs {2}")
  @CsvSource({
    "PC, SWITCH, STRAIGHT",
    "PC, HUB, STRAIGHT",
    "PC, BRIDGE, STRAIGHT",
    "PC, REPEATER, STRAIGHT",
    "SERVER, SWITCH, STRAIGHT",
    "ROUTER, SWITCH, STRAIGHT",
    "ROUTER, HUB, STRAIGHT",
    "PC, PC, CROSSOVER",
    "PC, SERVER, CROSSOVER",
    "PC, ROUTER, CROSSOVER",
    "ROUTER, ROUTER, CROSSOVER",
    "SWITCH, SWITCH, CROSSOVER",
    "SWITCH, HUB, CROSSOVER",
    "HUB, HUB, CROSSOVER",
    "BRIDGE, SWITCH, CROSSOVER",
    "HUB, REPEATER, CROSSOVER",
  })
  void strictModeDemandsExactlyTheCableThePinOutsCallFor(
      DeviceType first, DeviceType second, CableType expected) {
    Port a = ethernet(first);
    Port b = ethernet(second);
    CableRules strict = CableRules.strictCabling();

    assertThat(strict.workingCables(a, b)).containsExactly(expected);
    assertThat(strict.workingCables(b, a)).containsExactly(expected);
    assertThat(strict.recommendedCable(a, b)).contains(expected);
    CableType other = expected == CableType.STRAIGHT ? CableType.CROSSOVER : CableType.STRAIGHT;
    assertThat(strict.works(expected, a, b)).isTrue();
    assertThat(strict.works(other, a, b)).isFalse();
  }

  @ParameterizedTest(name = "{0} - {1} accepts either UTP cable")
  @CsvSource({"PC, PC, CROSSOVER", "PC, SWITCH, STRAIGHT", "SWITCH, SWITCH, CROSSOVER"})
  void autoMdixAcceptsEitherUtpCableButStillRecommendsTheCorrectOne(
      DeviceType first, DeviceType second, CableType recommended) {
    Port a = ethernet(first);
    Port b = ethernet(second);
    CableRules auto = CableRules.autoMdix();

    assertThat(auto.workingCables(a, b))
        .containsExactlyInAnyOrder(CableType.STRAIGHT, CableType.CROSSOVER);
    assertThat(auto.recommendedCable(a, b)).contains(recommended);
    assertThat(auto.works(CableType.SERIAL, a, b)).isFalse();
    assertThat(auto.works(CableType.CONSOLE, a, b)).isFalse();
  }

  @Test
  void serialCableJoinsTwoSerialPortsOnly() {
    Router first = topology.addRouter("R1", 1, 2);
    Router second = topology.addRouter("R2", 1, 1);
    Port serialA = first.port("S0/0/0");
    Port serialB = second.port("S0/0/0");
    CableRules strict = CableRules.strictCabling();

    assertThat(strict.workingCables(serialA, serialB)).containsExactly(CableType.SERIAL);
    assertThat(CableRules.autoMdix().workingCables(serialA, serialB))
        .containsExactly(CableType.SERIAL);
    assertThat(strict.recommendedCable(serialA, serialB)).contains(CableType.SERIAL);
    // A serial interface cannot be joined to an Ethernet port with any cable.
    assertThat(strict.workingCables(serialA, first.port("G0/0"))).isEmpty();
    assertThat(strict.recommendedCable(serialA, first.port("G0/0"))).isEqualTo(Optional.empty());
  }

  @Test
  void consoleCableJoinsAPcSerialPortToAConsolePort() {
    Pc pc = topology.addPc("PC");
    Switch sw = topology.addSwitch("SW");
    Router router = topology.addRouter("R");
    CableRules strict = CableRules.strictCabling();

    assertThat(strict.workingCables(pc.port("RS232"), sw.port("Console")))
        .containsExactly(CableType.CONSOLE);
    assertThat(strict.workingCables(router.port("Console"), pc.port("RS232")))
        .containsExactly(CableType.CONSOLE);
    assertThat(strict.recommendedCable(pc.port("RS232"), router.port("Console")))
        .contains(CableType.CONSOLE);
    // Two console ports, or two serial ports of PCs, make no sense.
    assertThat(strict.workingCables(sw.port("Console"), router.port("Console"))).isEmpty();
    assertThat(strict.workingCables(pc.port("RS232"), topology.addPc("PC2").port("RS232")))
        .isEmpty();
    assertThat(strict.workingCables(pc.port("RS232"), sw.port("Fa0/1"))).isEmpty();
  }

  @Test
  void factoriesSetTheStrictFlag() {
    assertThat(CableRules.strictCabling().strict()).isTrue();
    assertThat(CableRules.autoMdix().strict()).isFalse();
  }

  @Test
  void rejectsMissingArguments() {
    Port a = ethernet(DeviceType.PC);
    assertThatThrownBy(() -> CableRules.strictCabling().workingCables(null, a))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> CableRules.strictCabling().works(null, a, a))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> CableRules.strictCabling().recommendedCable(a, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void aNonEthernetPortHasNoPinOut() {
    Pc pc = topology.addPc("PC");
    assertThatThrownBy(() -> pc.port("RS232").mdi()).isInstanceOf(IllegalStateException.class);
    assertThat(pc.nic().mdi()).isEqualTo(MdiType.MDI);
    assertThat(topology.addSwitch("SW").port("Fa0/1").mdi()).isEqualTo(MdiType.MDI_X);
  }
}
