package io.github.mrramez.netlabstudio.core.net;

import java.util.List;
import java.util.Objects;

/**
 * The result of summarising several networks into one route.
 *
 * @param summary the single network that covers every input
 * @param extraNetworks the parts of {@code summary} that no input covers, as the fewest aligned
 *     blocks in ascending order; empty when the summary is exact
 * @param extraAddresses how many addresses {@code extraNetworks} hold in total
 */
public record RouteSummary(
    Ipv4Network summary, List<Ipv4Network> extraNetworks, long extraAddresses) {

  /** Rejects missing values and copies the list. */
  public RouteSummary {
    Objects.requireNonNull(summary, "summary");
    extraNetworks = List.copyOf(Objects.requireNonNull(extraNetworks, "extraNetworks"));
    if (extraAddresses < 0) {
      throw new IllegalArgumentException("extraAddresses must not be negative");
    }
  }

  /** The number of leading bits all inputs share, which is the summary's prefix length. */
  public int commonPrefixBits() {
    return summary.prefixLength();
  }

  /**
   * Whether the summary covers exactly the inputs. If not, a router using it would also attract
   * traffic for {@link #extraNetworks()}.
   */
  public boolean isExact() {
    return extraAddresses == 0;
  }
}
