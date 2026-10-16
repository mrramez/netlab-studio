package io.github.mrramez.netlabstudio.core.net;

/**
 * Thrown when a VLSM plan needs more address space than the base network has left.
 *
 * <p>It says which requirement could not be placed, so the UI can point at the row in the student's
 * list.
 */
public final class VlsmDoesNotFitException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  private final int requestIndex;
  private final int requestedHosts;
  private final long neededAddresses;
  private final long availableAddresses;

  /**
   * Creates the exception.
   *
   * @param requestIndex position of the failing requirement in the caller's list (0-based)
   * @param requestedHosts the hosts that requirement asked for
   * @param neededAddresses the size of the block that requirement needs
   * @param availableAddresses the addresses still unallocated in the base
   * @param base the base network, for the message
   */
  VlsmDoesNotFitException(
      int requestIndex,
      int requestedHosts,
      long neededAddresses,
      long availableAddresses,
      Ipv4Network base) {
    super(
        "cannot place requirement #"
            + (requestIndex + 1)
            + " ("
            + requestedHosts
            + " hosts): it needs a block of "
            + neededAddresses
            + " addresses, but only "
            + availableAddresses
            + " addresses are left in "
            + base);
    this.requestIndex = requestIndex;
    this.requestedHosts = requestedHosts;
    this.neededAddresses = neededAddresses;
    this.availableAddresses = availableAddresses;
  }

  /** Position of the requirement that did not fit, in the caller's list (0-based). */
  public int requestIndex() {
    return requestIndex;
  }

  /** The number of hosts that requirement asked for. */
  public int requestedHosts() {
    return requestedHosts;
  }

  /** The size of the block that requirement needs, in addresses. */
  public long neededAddresses() {
    return neededAddresses;
  }

  /** The addresses still unallocated in the base network. */
  public long availableAddresses() {
    return availableAddresses;
  }
}
