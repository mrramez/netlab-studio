package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.net.Ipv4Address;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of one echo request of a ping.
 *
 * @param sequence the sequence number, starting at 1
 * @param failure why it failed, or {@code null} if an echo reply arrived
 * @param failedAtDeviceId the device where the cause was found, or {@code null} on success
 * @param repliedFrom the address the answer came from: the destination on success, a router when it
 *     reported an error, or {@code null} if nothing came back
 * @param roundTripTicks ticks from sending the request to receiving the reply, or 0 on failure
 */
public record PingAttempt(
    int sequence,
    FailureReason failure,
    String failedAtDeviceId,
    Ipv4Address repliedFrom,
    long roundTripTicks) {

  /** Checks that success and failure are consistent. */
  public PingAttempt {
    if (sequence < 1) {
      throw new IllegalArgumentException("sequence starts at 1, but was " + sequence);
    }
    if (failure == null) {
      Objects.requireNonNull(repliedFrom, "repliedFrom (needed for a successful attempt)");
    } else {
      Objects.requireNonNull(failedAtDeviceId, "failedAtDeviceId (needed for a failed attempt)");
    }
  }

  /** Whether an echo reply arrived. */
  public boolean success() {
    return failure == null;
  }

  /** The failure, if there was one. */
  public Optional<FailureReason> reason() {
    return Optional.ofNullable(failure);
  }
}
