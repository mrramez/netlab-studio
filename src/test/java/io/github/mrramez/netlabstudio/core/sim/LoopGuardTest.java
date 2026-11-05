package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Switch;
import io.github.mrramez.netlabstudio.core.model.Topology;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** A loop of switches floods a broadcast forever; the engine must stop and say why. */
class LoopGuardTest {

  private final Topology topology = new Topology();

  /** Three switches in a triangle (no spanning tree), with A on the first. */
  private Host triangle() {
    Switch first = topology.addSwitch("SW1");
    Switch second = topology.addSwitch("SW2");
    Switch third = topology.addSwitch("SW3");
    Lab.cable(topology, first, "Fa0/1", second, "Fa0/1");
    Lab.cable(topology, second, "Fa0/2", third, "Fa0/2");
    Lab.cable(topology, third, "Fa0/3", first, "Fa0/3");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Lab.cable(topology, a, "Fa0", first, "Fa0/10");
    return a;
  }

  @Test
  @Timeout(10)
  void aBroadcastStormEndsAsAnExplainedFailureInsteadOfAHang() {
    Host a = triangle();
    SimulationEngine engine = new SimulationEngine(topology);

    PingResult result = engine.ping(a.id(), Lab.ip("192.168.1.99"), 3);

    assertThat(result.eventLimitReached()).isTrue();
    assertThat(result.events().size())
        .isBetween(
            SimulationSettings.DEFAULT_MAX_EVENTS, SimulationSettings.DEFAULT_MAX_EVENTS + 1);
    SimEvent last = result.events().get(result.events().size() - 1);
    assertThat(last.explanationKey()).isEqualTo("sim.engine.eventLimit");
    assertThat(last.action()).isEqualTo(SimAction.DROP);
    assertThat(last.args()).containsExactly(SimulationSettings.DEFAULT_MAX_EVENTS);
    // Every echo request is accounted for, all with the same explanation.
    assertThat(result.sent()).isEqualTo(3);
    assertThat(result.attempts())
        .allSatisfy(
            attempt -> {
              assertThat(attempt.reason()).contains(FailureReason.EVENT_LIMIT_EXCEEDED);
              assertThat(attempt.failedAtDeviceId()).isEqualTo(a.id());
            });
    assertThat(result.reachable()).isFalse();
  }

  @Test
  @Timeout(10)
  void theStormIsVisibleAsTheSameBroadcastAppearingOverAndOver() {
    Host a = triangle();

    PingResult result =
        new SimulationEngine(topology, SimulationSettings.defaults().withMaxEvents(300))
            .ping(a.id(), Lab.ip("192.168.1.99"), 1);

    long floods = result.events().stream().filter(e -> e.action() == SimAction.FLOOD).count();
    assertThat(floods).isGreaterThan(10);
    assertThat(result.events().size()).isEqualTo(301);
  }

  @Test
  @Timeout(10)
  void theLimitIsConfigurable() {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Switch sw = topology.addSwitch("SW");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    SimulationEngine tiny =
        new SimulationEngine(topology, SimulationSettings.defaults().withMaxEvents(12));

    PingResult result = tiny.ping(a.id(), Lab.ip("192.168.1.20"), 4);

    assertThat(result.eventLimitReached()).isTrue();
    assertThat(result.events()).hasSize(13);
    assertThat(result.attempts()).hasSize(4);
    assertThat(result.attempts()).allSatisfy(a1 -> assertThat(a1.success()).isFalse());
    // With the default limit the same ping is fine.
    assertThat(
            new SimulationEngine(topology)
                .ping(a.id(), Lab.ip("192.168.1.20"), 4)
                .eventLimitReached())
        .isFalse();
  }

  @Test
  @Timeout(10)
  void theGuardKeepsTheTimeOrderOfTheEvents() {
    Host a = triangle();

    PingResult result = new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.99"), 1);

    long previous = 0;
    for (SimEvent event : result.events()) {
      assertThat(event.time()).isGreaterThanOrEqualTo(previous);
      previous = event.time();
    }
  }

  @Test
  void aLoopOfTwoHubsAlsoEndsAtTheLimit() {
    var hub1 = topology.addHub("H1");
    var hub2 = topology.addHub("H2");
    Lab.cable(topology, hub1, "Port1", hub2, "Port1");
    Lab.cable(topology, hub1, "Port2", hub2, "Port2");
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Lab.cable(topology, a, "Fa0", hub1, "Port3");

    PingResult result =
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
            Duration.ofSeconds(10),
            () -> new SimulationEngine(topology).ping(a.id(), Lab.ip("192.168.1.99"), 1));

    assertThat(result.eventLimitReached()).isTrue();
  }
}
