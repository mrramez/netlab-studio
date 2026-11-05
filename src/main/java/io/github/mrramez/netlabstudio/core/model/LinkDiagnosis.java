package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.ExplanationStep;
import java.util.Objects;

/**
 * Why a link is up or down, seen from one of its ports, with an explanation the UI can show as a
 * tooltip or the simulator can quote when a ping fails.
 *
 * <p>The explanation is data (a message key plus arguments), never display text:
 *
 * <ul>
 *   <li>{@code model.link.up} - none
 *   <li>{@code model.link.notConnected} - port label
 *   <li>{@code model.link.adminDownLocal} - port label
 *   <li>{@code model.link.adminDownPeerRouter} - peer device name, peer port name; the peer is a
 *       router interface that needs {@code no shutdown}
 *   <li>{@code model.link.adminDownPeer} - peer device name, peer port name
 *   <li>{@code model.link.wrongCable} - label A, pin-out A, label B, pin-out B, cable used, cable
 *       required (names of the {@link CableType} and {@link MdiType} constants)
 *   <li>{@code model.link.incompatiblePorts} - label A, type A, label B, type B, cable used
 * </ul>
 *
 * @param state the link state
 * @param problem what is wrong, or {@link Problem#NONE}
 * @param explanation the explanation as a message key with arguments
 */
public record LinkDiagnosis(LinkState state, Problem problem, ExplanationStep explanation) {

  /** What keeps a link from coming up. */
  public enum Problem {
    /** Nothing is wrong. */
    NONE,
    /** No cable is plugged into the port. */
    NOT_CONNECTED,
    /** The port itself is shut down. */
    ADMIN_DOWN_LOCAL,
    /** The port at the other end is shut down. */
    ADMIN_DOWN_PEER,
    /** The cable type cannot work between these two ports. */
    WRONG_CABLE
  }

  /** Rejects missing parts. */
  public LinkDiagnosis {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(problem, "problem");
    Objects.requireNonNull(explanation, "explanation");
  }

  static LinkDiagnosis of(Port local, CableRules rules) {
    Link link = local.link().orElse(null);
    if (link == null) {
      return new LinkDiagnosis(
          LinkState.DOWN,
          Problem.NOT_CONNECTED,
          ExplanationStep.of("model.link.notConnected", local.label()));
    }
    Port peer = link.other(local);
    // A switched-off end wins over a wrong cable, like "administratively down" on a Cisco device.
    if (!local.isAdminUp()) {
      return new LinkDiagnosis(
          LinkState.ADMIN_DOWN,
          Problem.ADMIN_DOWN_LOCAL,
          ExplanationStep.of("model.link.adminDownLocal", local.label()));
    }
    if (!peer.isAdminUp()) {
      String key =
          peer.device().type() == DeviceType.ROUTER
              ? "model.link.adminDownPeerRouter"
              : "model.link.adminDownPeer";
      return new LinkDiagnosis(
          LinkState.ADMIN_DOWN,
          Problem.ADMIN_DOWN_PEER,
          ExplanationStep.of(key, peer.device().name(), peer.name()));
    }
    if (rules.works(link.cable(), local, peer)) {
      return new LinkDiagnosis(LinkState.UP, Problem.NONE, ExplanationStep.of("model.link.up"));
    }
    return new LinkDiagnosis(
        LinkState.DOWN, Problem.WRONG_CABLE, wrongCable(local, peer, link, rules));
  }

  private static ExplanationStep wrongCable(Port local, Port peer, Link link, CableRules rules) {
    return rules
        .recommendedCable(local, peer)
        .map(
            required ->
                ExplanationStep.of(
                    "model.link.wrongCable",
                    local.label(),
                    local.type().isEthernet() ? local.mdi().name() : local.type().name(),
                    peer.label(),
                    peer.type().isEthernet() ? peer.mdi().name() : peer.type().name(),
                    link.cable().name(),
                    required.name()))
        .orElseGet(
            () ->
                ExplanationStep.of(
                    "model.link.incompatiblePorts",
                    local.label(),
                    local.type().name(),
                    peer.label(),
                    peer.type().name(),
                    link.cable().name()));
  }
}
