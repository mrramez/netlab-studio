package io.github.mrramez.netlabstudio.core.net;

import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Makes MAC addresses for simulated devices, reproducibly: the same seed always gives the same
 * sequence, so saved topologies and tests see stable addresses.
 *
 * <p>The numbers come from SplitMix64, written out here so the sequence is fixed by this code and
 * the published reference vectors (checked in the tests) rather than by the JDK that runs it. Every
 * address is locally administered and unicast (U/L bit 1, I/G bit 0), so it can never be mistaken
 * for a real vendor address or for broadcast, and a generator never hands out the same address
 * twice.
 *
 * <p>Not thread-safe.
 */
public final class MacGenerator {

  private static final long MASK_48 = 0xFFFF_FFFF_FFFFL;
  private static final long GROUP_BIT = 0x0100_0000_0000L;
  private static final long LOCAL_BIT = 0x0200_0000_0000L;
  // A good source repeats an address with probability near 2^-46, so this is only a guard
  // against a broken source.
  private static final int MAX_ATTEMPTS = 1_000;

  private final LongSupplier source;
  private final Set<Long> issued = new HashSet<>();

  /** Creates a generator whose sequence is fixed by {@code seed}. */
  public MacGenerator(long seed) {
    this(new SplitMix64(seed));
  }

  // Package-private so tests can feed a source that repeats.
  MacGenerator(LongSupplier source) {
    this.source = source;
  }

  /**
   * The next address, different from every address this generator returned before.
   *
   * @throws IllegalStateException if the source keeps repeating addresses (cannot happen with a
   *     seeded generator in practice)
   */
  public MacAddress next() {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      long value = (nextRaw() & MASK_48 & ~GROUP_BIT) | LOCAL_BIT;
      if (issued.add(value)) {
        return new MacAddress(value);
      }
    }
    throw new IllegalStateException(
        "no new MAC address after " + MAX_ATTEMPTS + " attempts; the number source is repeating");
  }

  long nextRaw() {
    return source.getAsLong();
  }

  /** Steele, Lea and Flood's SplitMix64, with the constants of the reference implementation. */
  private static final class SplitMix64 implements LongSupplier {
    private long state;

    SplitMix64(long seed) {
      this.state = seed;
    }

    @Override
    public long getAsLong() {
      state += 0x9E37_79B9_7F4A_7C15L;
      long z = state;
      z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
      z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
      return z ^ (z >>> 31);
    }
  }
}
