package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import java.util.List;
import java.util.Objects;

/**
 * The result of {@link SimulationEngine#ping}: one {@link PingAttempt} per echo request and the
 * ordered events the UI replays step by step.
 *
 * @param sourceDeviceId the device that pinged
 * @param destination the address that was pinged
 * @param attempts one entry per echo request, in order
 * @param events everything that happened, in order; times never decrease
 * @param eventLimitReached whether the run was cut short by the event limit
 */
public record PingResult(
    String sourceDeviceId,
    Ipv4Address destination,
    List<PingAttempt> attempts,
    List<SimEvent> events,
    boolean eventLimitReached) {

  /** Copies the lists so a result can never change. */
  public PingResult {
    Objects.requireNonNull(sourceDeviceId, "sourceDeviceId");
    Objects.requireNonNull(destination, "destination");
    attempts = List.copyOf(attempts);
    events = List.copyOf(events);
  }

  /** The number of echo requests sent. */
  public int sent() {
    return attempts.size();
  }

  /** The number of echo replies received. */
  public int received() {
    return (int) attempts.stream().filter(PingAttempt::success).count();
  }

  /** The number of requests that got no echo reply. */
  public int lost() {
    return sent() - received();
  }

  /** Whether at least one echo reply arrived, i.e. the destination can be reached. */
  public boolean reachable() {
    return received() > 0;
  }

  /** Whether every echo request was answered. */
  public boolean allReceived() {
    return received() == sent();
  }
}
