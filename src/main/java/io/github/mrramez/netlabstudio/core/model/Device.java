package io.github.mrramez.netlabstudio.core.model;

import io.github.mrramez.netlabstudio.core.net.MacGenerator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A device in a {@link Topology}: its identity, a name, a position on the canvas and its ports.
 *
 * <p>The position is stored as plain doubles so the model stays independent of any UI toolkit. The
 * subclasses add the configuration that only that kind of device has (a PC's default gateway, for
 * instance). The set of device kinds is closed, so the simulator can switch over them exhaustively.
 * Not thread-safe.
 */
public abstract sealed class Device permits Host, Hub, Repeater, Bridge, Switch, Router {

  private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
  private static final int MAX_NAME_LENGTH = 64;

  private final String id;
  private final DeviceType type;
  private final List<Port> ports;
  private String name;
  private double x;
  private double y;

  Device(String id, String name, DeviceType type, List<PortSpec> specs, MacGenerator macs) {
    Objects.requireNonNull(id, "id");
    if (!ID.matcher(id).matches()) {
      throw new IllegalArgumentException(
          "device id must be 1 to 64 letters, digits, '.', '_' or '-', but was '" + id + "'");
    }
    this.id = id;
    this.type = Objects.requireNonNull(type, "type");
    this.name = checkName(name);
    List<Port> created = new ArrayList<>(specs.size());
    Set<String> names = new HashSet<>();
    for (PortSpec spec : specs) {
      if (!names.add(spec.name())) {
        throw new IllegalArgumentException("duplicate port name " + spec.name());
      }
      created.add(
          new Port(
              this, spec.name(), spec.type(), spec.hasMac() ? macs.next() : null, spec.adminUp()));
    }
    this.ports = List.copyOf(created);
  }

  /** A port to create: its name, connector, whether it has a MAC and its initial admin state. */
  record PortSpec(String name, PortType type, boolean hasMac, boolean adminUp) {}

  /** A unique id within the topology, stable for the life of the device. */
  public final String id() {
    return id;
  }

  /** The display name, e.g. {@code PC0} or {@code R1}. */
  public final String name() {
    return name;
  }

  /**
   * Renames the device.
   *
   * @throws IllegalArgumentException if the name is blank or longer than 64 characters
   */
  public final void setName(String name) {
    this.name = checkName(name);
  }

  /** The kind of device. */
  public final DeviceType type() {
    return type;
  }

  /** The horizontal canvas position. */
  public final double x() {
    return x;
  }

  /** The vertical canvas position. */
  public final double y() {
    return y;
  }

  /**
   * Moves the device on the canvas.
   *
   * @throws IllegalArgumentException if a coordinate is NaN or infinite
   */
  public final void setPosition(double x, double y) {
    if (!Double.isFinite(x) || !Double.isFinite(y)) {
      throw new IllegalArgumentException("position must be finite, but was " + x + ", " + y);
    }
    this.x = x;
    this.y = y;
  }

  /** All ports, in a fixed order. The list is unmodifiable. */
  public final List<Port> ports() {
    return ports;
  }

  /** The port with this exact name, if the device has one. */
  public final Optional<Port> findPort(String portName) {
    for (Port port : ports) {
      if (port.name().equals(portName)) {
        return Optional.of(port);
      }
    }
    return Optional.empty();
  }

  /**
   * The port with this exact name.
   *
   * @throws IllegalArgumentException if there is none
   */
  public final Port port(String portName) {
    return findPort(portName)
        .orElseThrow(
            () -> new IllegalArgumentException(name + " has no port named '" + portName + "'"));
  }

  private static String checkName(String name) {
    Objects.requireNonNull(name, "name");
    if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("device name must be 1 to 64 characters");
    }
    return name;
  }

  @Override
  public final String toString() {
    return name + " (" + type + ", " + id + ")";
  }
}
