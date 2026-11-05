package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.model.Switch;
import io.github.mrramez.netlabstudio.core.model.Topology;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What happens to the packet that triggers an ARP request. Hosts (Windows, Linux) hold it and send
 * it when the reply arrives. Cisco IOS routers drop it, which is why the first ping through a
 * router times out.
 */
class ArpFirstPacketTest {

  private final Topology topology = new Topology();

  private static List<String> keys(PingResult result) {
    return result.events().stream().map(SimEvent::explanationKey).toList();
  }

  private static List<Boolean> successes(PingResult result) {
    return result.attempts().stream().map(PingAttempt::success).toList();
  }

  // ---- PC to PC on one switch ----------------------------------------------------------------

  private record Lan(Host a, Host b, SimulationEngine engine) {}

  private Lan lan(SimulationSettings settings) {
    Host a = Lab.pc(topology, "A", "192.168.1.10", null);
    Host b = Lab.pc(topology, "B", "192.168.1.20", null);
    Switch sw = topology.addSwitch("SW");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    return new Lan(a, b, new SimulationEngine(topology, settings));
  }

  @Test
  void onOneSwitchEveryEchoOfTheVeryFirstPingIsAnswered() {
    Lan lan = lan(SimulationSettings.defaults());

    PingResult result = lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 4);

    assertThat(result.sent()).isEqualTo(4);
    assertThat(result.received()).isEqualTo(4);
    assertThat(result.allReceived()).isTrue();
    // The first reply takes longer: ARP (4 ticks) happens before the echo (4 ticks).
    assertThat(result.attempts())
        .extracting(PingAttempt::roundTripTicks)
        .containsExactly(8L, 4L, 4L, 4L);
    assertThat(keys(result))
        .contains("sim.arp.cacheMiss", "sim.arp.packetHeld", "sim.arp.packetReleased")
        .doesNotContain("sim.router.arpPacketDropped", "sim.ping.timeout");
  }

  @Test
  void theHeldPacketIsSentRightAfterTheArpReplyIsCached() {
    Lan lan = lan(SimulationSettings.defaults());

    List<String> keys = keys(lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 1));

    int held = keys.indexOf("sim.arp.packetHeld");
    int requested = keys.indexOf("sim.arp.requestSent");
    // B learns A from the request first; A caches B's answer last.
    int cached = keys.lastIndexOf("sim.arp.cacheUpdated");
    int released = keys.indexOf("sim.arp.packetReleased");
    assertThat(held).isNotNegative().isLessThan(requested);
    assertThat(requested).isLessThan(cached);
    assertThat(released).isEqualTo(cached + 1);
  }

  // ---- PC to PC across one router ------------------------------------------------------------

  private record Routed(Host a, Host b, Router r1, SimulationEngine engine) {}

  private Routed routed(SimulationSettings settings) {
    Host a = Lab.pc(topology, "A", "192.168.1.10", "192.168.1.1");
    Host b = topology.addPc("B");
    b.configure(Lab.ip("192.168.2.10"), Lab.mask("/24"), Lab.ip("192.168.2.1"));
    Router r1 = topology.addRouter("R1");
    Lab.configure(r1, "G0/0", "192.168.1.1", "/24");
    Lab.configure(r1, "G0/1", "192.168.2.1", "/24");
    Lab.cable(topology, a, "Fa0", r1, "G0/0");
    Lab.cable(topology, b, "Fa0", r1, "G0/1");
    return new Routed(a, b, r1, new SimulationEngine(topology, settings));
  }

  @Test
  void acrossARouterTheFirstAttemptIsLostWithTheRoutersArpExplanationAndTheNextThreeSucceed() {
    Routed lab = routed(SimulationSettings.defaults());

    PingResult result = lab.engine().ping(lab.a().id(), Lab.ip("192.168.2.10"), 4);

    assertThat(successes(result)).containsExactly(false, true, true, true);
    assertThat(result.received()).isEqualTo(3);
    assertThat(result.lost()).isEqualTo(1);

    PingAttempt first = result.attempts().get(0);
    assertThat(first.reason()).contains(FailureReason.LOST_TO_ARP);
    assertThat(first.failedAtDeviceId()).isEqualTo(lab.r1().id());

    // The PC held its packet and sent it; the router is the one that dropped it.
    SimEvent dropped =
        result.events().stream()
            .filter(e -> e.explanationKey().equals("sim.router.arpPacketDropped"))
            .findFirst()
            .orElseThrow();
    assertThat(dropped.deviceId()).isEqualTo(lab.r1().id());
    assertThat(dropped.portName()).isEqualTo("G0/1");
    assertThat(dropped.action()).isEqualTo(SimAction.DROP);
    assertThat(dropped.args()).containsExactly("192.168.2.10", "192.168.2.10");
    assertThat(keys(result).stream().filter("sim.router.arpPacketDropped"::equals)).hasSize(1);
    assertThat(keys(result).stream().filter("sim.arp.packetHeld"::equals)).hasSize(1);
    // The router's ARP request still went out and was answered: attempt 2 needs no further ARP.
    assertThat(result.attempts().get(1).roundTripTicks()).isEqualTo(4);
  }

  @Test
  void aRouterThatHoldsThePacketInsteadDeliversTheFirstPingToo() {
    Routed lab = routed(SimulationSettings.defaults().withRouterDropsFirstPacketWhileArping(false));

    PingResult result = lab.engine().ping(lab.a().id(), Lab.ip("192.168.2.10"), 4);

    assertThat(successes(result)).containsExactly(true, true, true, true);
    assertThat(keys(result)).doesNotContain("sim.router.arpPacketDropped");
    // Held twice: once by A (waiting for the router's MAC) and once by R1 (waiting for B's).
    assertThat(keys(result).stream().filter("sim.arp.packetHeld"::equals)).hasSize(2);
    assertThat(keys(result).stream().filter("sim.arp.packetReleased"::equals)).hasSize(2);
  }

  // ---- warm caches ---------------------------------------------------------------------------

  @Test
  void aSecondRunWithWarmArpCachesAnswersAllFourOnTheSwitchedLan() {
    Lan lan = lan(SimulationSettings.defaults());
    lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 4);

    PingResult second = lan.engine().ping(lan.a().id(), Lab.ip("192.168.1.20"), 4);

    assertThat(second.received()).isEqualTo(4);
    assertThat(second.attempts()).extracting(PingAttempt::roundTripTicks).containsOnly(4L);
    assertThat(keys(second)).doesNotContain("sim.arp.cacheMiss", "sim.arp.requestSent");
  }

  @Test
  void aSecondRunWithWarmArpCachesAnswersAllFourAcrossTheRouter() {
    Routed lab = routed(SimulationSettings.defaults());
    lab.engine().ping(lab.a().id(), Lab.ip("192.168.2.10"), 4);

    PingResult second = lab.engine().ping(lab.a().id(), Lab.ip("192.168.2.10"), 4);

    assertThat(successes(second)).containsExactly(true, true, true, true);
    assertThat(second.attempts()).extracting(PingAttempt::roundTripTicks).containsOnly(4L);
    assertThat(keys(second))
        .doesNotContain("sim.arp.cacheMiss", "sim.arp.requestSent", "sim.router.arpPacketDropped");
  }

  @Test
  void aRouterThatForgotBothHostsLosesOnePacketPerDirection() {
    Routed lab = routed(SimulationSettings.defaults());
    lab.engine().ping(lab.a().id(), Lab.ip("192.168.2.10"), 4);

    lab.engine().clearArpTable(lab.r1().id());
    PingResult again = lab.engine().ping(lab.a().id(), Lab.ip("192.168.2.10"), 4);

    // A still knows the router, but the router forgot both hosts and drops one packet for each:
    // the echo request (it must ask for B) and then B's reply (it must ask for A).
    assertThat(successes(again)).containsExactly(false, false, true, true);
    assertThat(again.attempts().get(0).reason()).contains(FailureReason.LOST_TO_ARP);
    assertThat(again.attempts().get(1).reason()).contains(FailureReason.LOST_TO_ARP);
    assertThat(keys(again).stream().filter("sim.router.arpPacketDropped"::equals)).hasSize(2);
  }

  @Test
  void aHostsFirstPacketIsHeldWhateverTheRouterSettingIs() {
    for (boolean routerDrops : new boolean[] {true, false}) {
      Topology local = new Topology();
      Host a = Lab.pc(local, "A", "192.168.1.10", null);
      Host b = Lab.pc(local, "B", "192.168.1.20", null);
      Lab.cable(local, a, "Fa0", b, "Fa0");
      SimulationSettings settings =
          SimulationSettings.defaults().withRouterDropsFirstPacketWhileArping(routerDrops);

      PingResult result =
          new SimulationEngine(local, settings).ping(a.id(), Lab.ip("192.168.1.20"), 4);

      assertThat(result.allReceived()).as("routerDrops=%s", routerDrops).isTrue();
    }
  }
}
