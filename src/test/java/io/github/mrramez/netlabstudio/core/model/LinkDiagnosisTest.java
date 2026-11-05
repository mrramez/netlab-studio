package io.github.mrramez.netlabstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mrramez.netlabstudio.core.model.LinkDiagnosis.Problem;
import io.github.mrramez.netlabstudio.core.net.ExplanationStep;
import org.junit.jupiter.api.Test;

/** Link colours and the reasons behind them (SPEC §5, "Link state"). */
class LinkDiagnosisTest {

  private final Topology topology = new Topology();

  @Test
  void aWorkingLinkIsUp() {
    Pc pc = topology.addPc("PC0");
    Switch sw = topology.addSwitch("SW0");
    topology.connect(pc.nic(), sw.port("Fa0/1"), CableType.STRAIGHT);

    LinkDiagnosis diagnosis = topology.diagnose(pc.nic());

    assertThat(diagnosis.state()).isEqualTo(LinkState.UP);
    assertThat(diagnosis.problem()).isEqualTo(Problem.NONE);
    assertThat(diagnosis.explanation().key()).isEqualTo("model.link.up");
  }

  @Test
  void aPortWithoutACableIsDown() {
    Pc pc = topology.addPc("PC0");

    LinkDiagnosis diagnosis = topology.diagnose(pc.nic());

    assertThat(diagnosis.state()).isEqualTo(LinkState.DOWN);
    assertThat(diagnosis.problem()).isEqualTo(Problem.NOT_CONNECTED);
    assertThat(diagnosis.explanation())
        .isEqualTo(ExplanationStep.of("model.link.notConnected", "PC0 Fa0"));
  }

  @Test
  void aRouterInterfaceThatIsStillShutDownMakesTheLinkAmberOnBothEnds() {
    Pc pc = topology.addPc("PC0");
    Router router = topology.addRouter("R1");
    Link link = topology.connect(pc.nic(), router.port("G0/0"), CableType.CROSSOVER);

    assertThat(link.state()).isEqualTo(LinkState.ADMIN_DOWN);

    LinkDiagnosis fromPc = topology.diagnose(pc.nic());
    assertThat(fromPc.problem()).isEqualTo(Problem.ADMIN_DOWN_PEER);
    // A dedicated key for a router peer, so its message can name the fix: "no shutdown".
    assertThat(fromPc.explanation())
        .isEqualTo(ExplanationStep.of("model.link.adminDownPeerRouter", "R1", "G0/0"));

    LinkDiagnosis fromRouter = topology.diagnose(router.port("G0/0"));
    assertThat(fromRouter.problem()).isEqualTo(Problem.ADMIN_DOWN_LOCAL);
    assertThat(fromRouter.explanation())
        .isEqualTo(ExplanationStep.of("model.link.adminDownLocal", "R1 G0/0"));

    router.port("G0/0").setAdminUp(true);
    assertThat(link.state()).isEqualTo(LinkState.UP);
  }

  @Test
  void aShutDownSwitchPortUsesTheGenericPeerMessage() {
    Pc pc = topology.addPc("PC0");
    Switch sw = topology.addSwitch("SW0");
    topology.connect(pc.nic(), sw.port("Fa0/1"), CableType.STRAIGHT);
    sw.port("Fa0/1").setAdminUp(false);

    LinkDiagnosis diagnosis = topology.diagnose(pc.nic());

    assertThat(diagnosis.state()).isEqualTo(LinkState.ADMIN_DOWN);
    assertThat(diagnosis.explanation())
        .isEqualTo(ExplanationStep.of("model.link.adminDownPeer", "SW0", "Fa0/1"));
  }

  @Test
  void aStraightCableBetweenTwoPcsIsADownLinkThatSuggestsCrossover() {
    Pc a = topology.addPc("A");
    Pc b = topology.addPc("B");
    Link link = topology.connect(a.nic(), b.nic(), CableType.STRAIGHT);

    LinkDiagnosis diagnosis = topology.diagnose(a.nic());

    assertThat(link.state()).isEqualTo(LinkState.DOWN);
    assertThat(diagnosis.problem()).isEqualTo(Problem.WRONG_CABLE);
    assertThat(diagnosis.explanation())
        .isEqualTo(
            ExplanationStep.of(
                "model.link.wrongCable", "A Fa0", "MDI", "B Fa0", "MDI", "STRAIGHT", "CROSSOVER"));
  }

  @Test
  void aCrossoverCableToASwitchSuggestsStraightThrough() {
    Pc pc = topology.addPc("PC");
    Switch sw = topology.addSwitch("SW");
    topology.connect(pc.nic(), sw.port("Fa0/1"), CableType.CROSSOVER);

    ExplanationStep step = topology.diagnose(sw.port("Fa0/1")).explanation();

    assertThat(step.key()).isEqualTo("model.link.wrongCable");
    assertThat(step.args())
        .containsExactly("SW Fa0/1", "MDI_X", "PC Fa0", "MDI", "CROSSOVER", "STRAIGHT");
  }

  @Test
  void aCableThatFitsNeitherPortIsReportedAsIncompatible() {
    Router router = topology.addRouter("R", 1, 1);
    Pc pc = topology.addPc("PC");
    topology.connect(router.port("S0/0/0"), pc.nic(), CableType.SERIAL);
    router.port("S0/0/0").setAdminUp(true);

    LinkDiagnosis diagnosis = topology.diagnose(pc.nic());

    assertThat(diagnosis.problem()).isEqualTo(Problem.WRONG_CABLE);
    assertThat(diagnosis.explanation())
        .isEqualTo(
            ExplanationStep.of(
                "model.link.incompatiblePorts",
                "PC Fa0",
                "FAST_ETHERNET",
                "R S0/0/0",
                "SERIAL",
                "SERIAL"));
  }

  @Test
  void aSwitchedOffEndIsReportedBeforeAWrongCable() {
    Pc a = topology.addPc("A");
    Router router = topology.addRouter("R");
    // PC and router are both MDI, so a straight cable is wrong; the interface is also shut down.
    topology.connect(a.nic(), router.port("G0/0"), CableType.STRAIGHT);

    assertThat(topology.diagnose(a.nic()).problem()).isEqualTo(Problem.ADMIN_DOWN_PEER);
    router.port("G0/0").setAdminUp(true);
    assertThat(topology.diagnose(a.nic()).problem()).isEqualTo(Problem.WRONG_CABLE);
  }

  @Test
  void autoMdixMakesTheWrongCableWork() {
    Pc a = topology.addPc("A");
    Pc b = topology.addPc("B");
    topology.connect(a.nic(), b.nic(), CableType.STRAIGHT);

    topology.setCableRules(CableRules.autoMdix());

    assertThat(topology.diagnose(a.nic()).state()).isEqualTo(LinkState.UP);
  }

  @Test
  void aConsoleLinkIsUpWithTheRightCable() {
    Pc pc = topology.addPc("PC");
    Switch sw = topology.addSwitch("SW");
    topology.connect(pc.port("RS232"), sw.port("Console"), CableType.CONSOLE);

    assertThat(topology.diagnose(pc.port("RS232")).state()).isEqualTo(LinkState.UP);
  }

  @Test
  void rejectsMissingParts() {
    assertThatThrownBy(
            () -> new LinkDiagnosis(null, Problem.NONE, ExplanationStep.of("model.link.up")))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new LinkDiagnosis(LinkState.UP, null, ExplanationStep.of("model.link.up")))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new LinkDiagnosis(LinkState.UP, Problem.NONE, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> topology.diagnose(null)).isInstanceOf(NullPointerException.class);
  }
}
