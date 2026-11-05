package io.github.mrramez.netlabstudio.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mrramez.netlabstudio.core.model.Bridge;
import io.github.mrramez.netlabstudio.core.model.CableType;
import io.github.mrramez.netlabstudio.core.model.Host;
import io.github.mrramez.netlabstudio.core.model.Hub;
import io.github.mrramez.netlabstudio.core.model.Pc;
import io.github.mrramez.netlabstudio.core.model.Repeater;
import io.github.mrramez.netlabstudio.core.model.Router;
import io.github.mrramez.netlabstudio.core.model.Switch;
import io.github.mrramez.netlabstudio.core.model.Topology;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Collision and broadcast domain counting (CURRICULUM lesson 5, "Count the domains"). */
class NetworkDomainsTest {

  private final Topology topology = new Topology();

  private NetworkDomains analyze() {
    return new SimulationEngine(topology).domains();
  }

  @Test
  void anEmptyTopologyHasNoDomains() {
    NetworkDomains domains = analyze();

    assertThat(domains.collisionDomains()).isEmpty();
    assertThat(domains.broadcastDomains()).isEmpty();
  }

  @Test
  void fourPcsOnAHubAreOneCollisionDomainAndOneBroadcastDomain() {
    Hub hub = topology.addHub("HUB");
    for (int i = 1; i <= 4; i++) {
      Lab.cable(topology, topology.addPc("PC" + i), "Fa0", hub, "Port" + i);
    }

    NetworkDomains domains = analyze();

    assertThat(domains.collisionDomains()).hasSize(1);
    assertThat(domains.broadcastDomains()).hasSize(1);
    NetworkDomain only = domains.collisionDomains().get(0);
    assertThat(only.id()).isEqualTo("collision-1");
    assertThat(only.linkIds()).hasSize(4);
    assertThat(only.deviceIds()).hasSize(5);
    assertThat(only.portIds()).hasSize(8);
  }

  @Test
  void fourPcsOnASwitchAreFourCollisionDomainsButOneBroadcastDomain() {
    Switch sw = topology.addSwitch("SW");
    for (int i = 1; i <= 4; i++) {
      Lab.cable(topology, topology.addPc("PC" + i), "Fa0", sw, "Fa0/" + i);
    }

    NetworkDomains domains = analyze();

    assertThat(domains.collisionDomains()).hasSize(4);
    assertThat(domains.collisionDomains())
        .extracting(NetworkDomain::id)
        .containsExactly("collision-1", "collision-2", "collision-3", "collision-4");
    assertThat(domains.collisionDomains()).allSatisfy(d -> assertThat(d.linkIds()).hasSize(1));
    assertThat(domains.broadcastDomains()).hasSize(1);
    assertThat(domains.broadcastDomains().get(0).id()).isEqualTo("broadcast-1");
    assertThat(domains.broadcastDomains().get(0).linkIds()).hasSize(4);
    // The switch belongs to every collision domain it has a port in.
    assertThat(domains.collisionDomains())
        .allSatisfy(d -> assertThat(d.deviceIds()).contains(sw.id()));
  }

  @Test
  void aBridgeSplitsCollisionDomainsLikeASwitch() {
    Bridge bridge = topology.addBridge("BR");
    Hub left = topology.addHub("LEFT");
    Hub right = topology.addHub("RIGHT");
    Lab.cable(topology, left, "Port1", bridge, "Port1");
    Lab.cable(topology, right, "Port1", bridge, "Port2");
    Lab.cable(topology, topology.addPc("A"), "Fa0", left, "Port2");
    Lab.cable(topology, topology.addPc("B"), "Fa0", left, "Port3");
    Lab.cable(topology, topology.addPc("C"), "Fa0", right, "Port2");

    NetworkDomains domains = analyze();

    assertThat(domains.collisionDomains()).hasSize(2);
    assertThat(domains.broadcastDomains()).hasSize(1);
  }

  @Test
  void aHubPluggedIntoASwitchJoinsTheUplinkToItsCollisionDomain() {
    Switch sw = topology.addSwitch("SW");
    Hub hub = topology.addHub("HUB");
    Lab.cable(topology, hub, "Port1", sw, "Fa0/1");
    for (int i = 2; i <= 4; i++) {
      Lab.cable(topology, topology.addPc("H" + i), "Fa0", hub, "Port" + i);
    }
    Pc alone = topology.addPc("Alone");
    Lab.cable(topology, alone, "Fa0", sw, "Fa0/2");

    NetworkDomains domains = analyze();

    // One domain for the hub segment (3 PCs and the uplink), one for the lone PC.
    assertThat(domains.collisionDomains()).hasSize(2);
    assertThat(domains.collisionDomains().get(0).linkIds()).hasSize(4);
    assertThat(domains.collisionDomains().get(1).linkIds()).hasSize(1);
    assertThat(domains.broadcastDomains()).hasSize(1);
  }

  @Test
  void aRouterWithTwoLansHasTwoBroadcastDomains() {
    Router router = topology.addRouter("R1");
    Switch left = topology.addSwitch("SW1");
    Switch right = topology.addSwitch("SW2");
    Lab.cable(topology, router, "G0/0", left, "Fa0/1");
    Lab.cable(topology, router, "G0/1", right, "Fa0/1");
    Lab.cable(topology, topology.addPc("A"), "Fa0", left, "Fa0/2");
    Lab.cable(topology, topology.addPc("B"), "Fa0", left, "Fa0/3");
    Lab.cable(topology, topology.addPc("C"), "Fa0", right, "Fa0/2");
    Lab.cable(topology, topology.addPc("D"), "Fa0", right, "Fa0/3");
    router.port("G0/0").setAdminUp(true);
    router.port("G0/1").setAdminUp(true);

    NetworkDomains domains = analyze();

    assertThat(domains.broadcastDomains()).hasSize(2);
    assertThat(domains.broadcastDomains())
        .allSatisfy(
            d -> {
              assertThat(d.deviceIds()).contains(router.id());
              assertThat(d.linkIds()).hasSize(3);
            });
    // Every cable is a collision domain of its own: 3 on each side.
    assertThat(domains.collisionDomains()).hasSize(6);
  }

  @Test
  void twoRoutersAddADomainForTheirLink() {
    Router first = topology.addRouter("R1");
    Router second = topology.addRouter("R2");
    first.port("G0/1").setAdminUp(true);
    second.port("G0/0").setAdminUp(true);
    Lab.cable(topology, first, "G0/1", second, "G0/0");
    Lab.cable(topology, topology.addPc("A"), "Fa0", first, "G0/0");
    first.port("G0/0").setAdminUp(true);
    Lab.cable(topology, topology.addPc("B"), "Fa0", second, "G0/1");
    second.port("G0/1").setAdminUp(true);

    NetworkDomains domains = analyze();

    assertThat(domains.broadcastDomains()).hasSize(3);
    assertThat(domains.collisionDomains()).hasSize(3);
  }

  @Test
  void twoPcsOnACrossoverCableAreOneOfEach() {
    Host a = topology.addPc("A");
    Host b = topology.addPc("B");
    Lab.cable(topology, a, "Fa0", b, "Fa0");

    NetworkDomains domains = analyze();

    assertThat(domains.collisionDomains()).hasSize(1);
    assertThat(domains.broadcastDomains()).hasSize(1);
  }

  @Test
  void aRepeaterExtendsTheCollisionDomain() {
    Repeater repeater = topology.addRepeater("REP");
    Lab.cable(topology, topology.addPc("A"), "Fa0", repeater, "Port1");
    Lab.cable(topology, topology.addPc("B"), "Fa0", repeater, "Port2");

    NetworkDomains domains = analyze();

    assertThat(domains.collisionDomains()).hasSize(1);
    assertThat(domains.collisionDomains().get(0).linkIds()).hasSize(2);
  }

  @Test
  void linksThatAreDownCarryNothingAndAreNotCounted() {
    Switch sw = topology.addSwitch("SW");
    Pc good = topology.addPc("Good");
    Pc wrongCable = topology.addPc("Wrong");
    Pc shut = topology.addPc("Shut");
    Lab.cable(topology, good, "Fa0", sw, "Fa0/1");
    topology.connect(wrongCable.nic(), sw.port("Fa0/2"), CableType.CROSSOVER);
    Lab.cable(topology, shut, "Fa0", sw, "Fa0/3");
    sw.port("Fa0/3").setAdminUp(false);
    topology.addPc("Unplugged");

    NetworkDomains domains = analyze();

    assertThat(domains.collisionDomains()).hasSize(1);
    assertThat(domains.broadcastDomains()).hasSize(1);
    assertThat(domains.broadcastDomains().get(0).deviceIds()).containsExactly(good.id(), sw.id());
  }

  @Test
  void aConsoleCableIsNotADataLink() {
    Pc pc = topology.addPc("PC");
    Switch sw = topology.addSwitch("SW");
    topology.connect(pc.port("RS232"), sw.port("Console"), CableType.CONSOLE);

    assertThat(analyze().collisionDomains()).isEmpty();
    assertThat(analyze().broadcastDomains()).isEmpty();
  }

  @Test
  void domainsAreLookedUpByPort() {
    Switch sw = topology.addSwitch("SW");
    Pc a = topology.addPc("A");
    Pc b = topology.addPc("B");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");
    topology.addPc("Unplugged");

    NetworkDomains domains = analyze();

    assertThat(domains.collisionDomainOf(a.nic())).isPresent();
    assertThat(domains.collisionDomainOf(a.nic())).isNotEqualTo(domains.collisionDomainOf(b.nic()));
    assertThat(domains.collisionDomainOf(sw.port("Fa0/1")))
        .isEqualTo(domains.collisionDomainOf(a.nic()));
    assertThat(domains.broadcastDomainOf(a.nic())).isEqualTo(domains.broadcastDomainOf(b.nic()));
    assertThat(domains.collisionDomainOf(sw.port("Fa0/9"))).isEmpty();
    assertThat(domains.broadcastDomainOf(sw.port("Fa0/9"))).isEmpty();
  }

  @Test
  void theResultIsDeterministicAndFollowsTheTopologyOrder() {
    Switch sw = topology.addSwitch("SW");
    Pc a = topology.addPc("A");
    Pc b = topology.addPc("B");
    Lab.cable(topology, a, "Fa0", sw, "Fa0/1");
    Lab.cable(topology, b, "Fa0", sw, "Fa0/2");

    assertThat(analyze()).isEqualTo(analyze());
    assertThat(analyze().collisionDomains().get(0).deviceIds()).containsExactly(a.id(), sw.id());
    assertThat(analyze().collisionDomains().get(1).deviceIds()).containsExactly(b.id(), sw.id());
  }

  @Test
  void domainObjectsCannotBeChanged() {
    Hub hub = topology.addHub("HUB");
    Lab.cable(topology, topology.addPc("A"), "Fa0", hub, "Port1");
    NetworkDomains domains = analyze();

    assertThatThrownBy(() -> domains.collisionDomains().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> domains.collisionDomains().get(0).portIds().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new NetworkDomain(null, Set.of(), Set.of(), Set.of()))
        .isInstanceOf(RuntimeException.class);
  }
}
