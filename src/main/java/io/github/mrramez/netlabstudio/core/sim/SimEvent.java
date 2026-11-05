package io.github.mrramez.netlabstudio.core.sim;

import io.github.mrramez.netlabstudio.core.net.ExplanationStep;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One step of a simulation: who did what, at which OSI layer, to which PDU, and why.
 *
 * <p>The explanation is data, not text: {@code explanationKey} is a message key and {@code args}
 * are the values to put into it (addresses as text, numbers as {@link Integer}), so the UI can show
 * the same step in English or Arabic. See {@link ExplanationKey} for the keys the simulator emits
 * and what each argument means.
 *
 * @param time the virtual time in ticks since the start of the run (a frame needs one tick to cross
 *     a cable); events with equal times happen "at the same moment", in list order
 * @param deviceId the id of the device that did it
 * @param portName the port involved, or {@code null} if the step belongs to the device as a whole
 *     (a forwarding decision, for instance)
 * @param layer the OSI layer of the step, 1 to 7
 * @param action what happened
 * @param pdu a snapshot of the PDU involved, or {@code null} if there is none; PDUs are immutable
 * @param explanationKey the message key of the explanation
 * @param args the values for the message, immutable
 */
public record SimEvent(
    long time,
    String deviceId,
    String portName,
    int layer,
    SimAction action,
    Pdu pdu,
    String explanationKey,
    List<Object> args) {

  /**
   * Validates the fields; the key and the arguments follow the rules of {@link ExplanationStep}.
   */
  public SimEvent {
    if (time < 0) {
      throw new IllegalArgumentException("time must not be negative, but was " + time);
    }
    Objects.requireNonNull(deviceId, "deviceId");
    if (deviceId.isBlank()) {
      throw new IllegalArgumentException("deviceId must not be blank");
    }
    if (layer < 1 || layer > 7) {
      throw new IllegalArgumentException("layer must be 1 to 7, but was " + layer);
    }
    Objects.requireNonNull(action, "action");
    args = new ExplanationStep(explanationKey, args).args();
  }

  /** Whether the event belongs to a particular port. */
  public boolean hasPort() {
    return portName != null;
  }

  /** Whether the event carries a PDU snapshot. */
  public boolean hasPdu() {
    return pdu != null;
  }

  /** The explanation as a key with arguments. */
  public ExplanationStep explanation() {
    return new ExplanationStep(explanationKey, args);
  }

  /** The frame this event shows, if its PDU is a frame. */
  public Optional<EthernetFrame> frame() {
    return pdu instanceof EthernetFrame frame ? Optional.of(frame) : Optional.empty();
  }
}
