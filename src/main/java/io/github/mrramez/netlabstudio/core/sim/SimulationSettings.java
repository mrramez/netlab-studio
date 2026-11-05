package io.github.mrramez.netlabstudio.core.sim;

/**
 * Tunable parameters of a {@link SimulationEngine}.
 *
 * @param maxEvents the most events one {@code ping} call may produce before it is stopped as a loop
 * @param hostTtl the initial TTL of packets created by PCs and servers (Windows uses 128)
 * @param routerTtl the initial TTL of packets created by routers (Cisco IOS uses 255)
 * @param hostDropsFirstPacketWhileArping whether a host drops the packet that triggered an ARP
 *     request instead of holding it (true, as Cisco IOS and Packet Tracer do, so the first ping is
 *     lost); routers always hold the packet
 */
public record SimulationSettings(
    int maxEvents, int hostTtl, int routerTtl, boolean hostDropsFirstPacketWhileArping) {

  /** The default cap on events per run. */
  public static final int DEFAULT_MAX_EVENTS = 5_000;

  private static final int MIN_EVENTS = 10;
  private static final int MAX_EVENTS = 1_000_000;

  /** Validates the ranges. */
  public SimulationSettings {
    if (maxEvents < MIN_EVENTS || maxEvents > MAX_EVENTS) {
      throw new IllegalArgumentException(
          "maxEvents must be between "
              + MIN_EVENTS
              + " and "
              + MAX_EVENTS
              + ", but was "
              + maxEvents);
    }
    checkTtl(hostTtl);
    checkTtl(routerTtl);
  }

  /** The defaults: 5000 events, host TTL 128, router TTL 255, first packet lost to ARP. */
  public static SimulationSettings defaults() {
    return new SimulationSettings(DEFAULT_MAX_EVENTS, 128, 255, true);
  }

  /** A copy with another event limit. */
  public SimulationSettings withMaxEvents(int value) {
    return new SimulationSettings(value, hostTtl, routerTtl, hostDropsFirstPacketWhileArping);
  }

  /** A copy with another host TTL. */
  public SimulationSettings withHostTtl(int value) {
    return new SimulationSettings(maxEvents, value, routerTtl, hostDropsFirstPacketWhileArping);
  }

  /** A copy with another router TTL. */
  public SimulationSettings withRouterTtl(int value) {
    return new SimulationSettings(maxEvents, hostTtl, value, hostDropsFirstPacketWhileArping);
  }

  /** A copy that makes hosts hold (false) or drop (true) the packet that triggers ARP. */
  public SimulationSettings withHostDropsFirstPacketWhileArping(boolean value) {
    return new SimulationSettings(maxEvents, hostTtl, routerTtl, value);
  }

  private static void checkTtl(int ttl) {
    if (ttl < 1 || ttl > 255) {
      throw new IllegalArgumentException("TTL must be between 1 and 255, but was " + ttl);
    }
  }
}
