package io.github.mrramez.netlabstudio.core.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Subnetting calculations as taught in the course, each returned with a worked solution.
 *
 * <p>Counting follows the exam rules: 2<sup>s</sup> subnets (subnet zero and the all-ones subnet
 * are allowed, as in current Cisco IOS; older material subtracts 2) and 2<sup>h</sup> - 2 hosts.
 *
 * <p>Explanation keys and their arguments (all argument values are {@code String}, {@code Integer},
 * {@code Long} or {@code Boolean}):
 *
 * <ul>
 *   <li>{@code net.plan.base} (base): the network that is divided
 *   <li>{@code net.plan.classfulBase} (address, class, default mask, base): the base was derived
 *       from an address through its class
 *   <li>{@code net.plan.borrowForSubnets} (required subnets, borrowed bits, subnets): smallest s
 *       with 2<sup>s</sup> at least the requirement
 *   <li>{@code net.plan.newMask} (base prefix, borrowed bits, new prefix, new mask)
 *   <li>{@code net.plan.hostsPerSubnet} (host bits, hosts)
 *   <li>{@code net.plan.hostBitsNeeded} (required hosts, host bits, hosts): smallest h with
 *       2<sup>h</sup> - 2 at least the requirement
 *   <li>{@code net.plan.newMaskFromHosts} (host bits, new prefix, new mask)
 *   <li>{@code net.plan.borrowedBits} (base prefix, new prefix, borrowed bits, subnets)
 *   <li>{@code net.plan.blockSize} (interesting octet, mask value in that octet, block size)
 *   <li>{@code net.plan.subnetZeroNote}: older material subtracts 2 from the subnet count
 *   <li>{@code net.list.start} (base, new prefix, interesting octet, block size)
 *   <li>{@code net.list.limited} (requested, available): fewer subnets exist than requested
 *   <li>{@code net.list.subnet} (number, subnet, first usable, last usable, broadcast)
 *   <li>{@code net.subnet.hostBinary} (host, binary, prefix)
 *   <li>{@code net.subnet.maskBinary} (mask, binary, prefix)
 *   <li>{@code net.subnet.andResult} (network binary, network, prefix): host AND mask
 *   <li>{@code net.subnet.broadcast} (broadcast binary, broadcast, host bits): host bits all 1
 *   <li>{@code net.subnet.range} (first usable, last usable, usable hosts)
 *   <li>{@code net.subnet.pointToPoint}, {@code net.subnet.singleHost}: notes for /31 and /32
 *   <li>{@code net.subnet.blockSize} (interesting octet, mask value in that octet, block size)
 *   <li>{@code net.classful.class} (address, class, default prefix, default mask)
 *   <li>{@code net.classful.borrowed} (prefix, default prefix, borrowed bits)
 *   <li>{@code net.classful.subnetCount} (borrowed bits, subnets)
 *   <li>{@code net.classful.supernet} (prefix, default prefix, supernet bits, joined networks)
 *   <li>{@code net.classful.hostsPerSubnet} (host bits, hosts)
 *   <li>{@code net.classful.rfc3021}, {@code net.classful.singleHost}, {@code
 *       net.classful.subnetZeroNote}: notes
 * </ul>
 */
public final class SubnetCalculator {

  /** The most subnets {@link #listSubnets} returns, so a UI cannot ask for millions of rows. */
  static final int MAX_LISTED_SUBNETS = 1024;

  // A subnet needs room for the network and broadcast addresses plus at least one host.
  private static final int LONGEST_SUBNET_PREFIX = 30;

  private SubnetCalculator() {}

  /**
   * Divides {@code base} into at least {@code requiredSubnets} equal subnets.
   *
   * @throws IllegalArgumentException if fewer than 1 subnet is requested, or the subnets would have
   *     fewer than 2 host bits
   */
  public static Explained<SubnetPlan> bySubnetCount(Ipv4Network base, int requiredSubnets) {
    Objects.requireNonNull(base, "base");
    return planBySubnets(
        base, requiredSubnets, ExplanationStep.of("net.plan.base", base.toString()));
  }

  /**
   * Like {@link #bySubnetCount(Ipv4Network, int)}, starting from the classful network of {@code
   * address} (for example 192.14.2.0/24 for any class C address in 192.14.2.x).
   *
   * @throws IllegalArgumentException additionally if the address is in class D or E
   */
  public static Explained<SubnetPlan> bySubnetCount(Ipv4Address address, int requiredSubnets) {
    Ipv4Network base = classfulNetwork(address);
    return planBySubnets(base, requiredSubnets, classfulBaseStep(address, base));
  }

  /**
   * Divides {@code base} into equal subnets that each hold at least {@code requiredHosts} hosts.
   *
   * @throws IllegalArgumentException if fewer than 1 host is requested, or the base has too few
   *     host bits
   */
  public static Explained<SubnetPlan> byHostCount(Ipv4Network base, int requiredHosts) {
    Objects.requireNonNull(base, "base");
    return planByHosts(base, requiredHosts, ExplanationStep.of("net.plan.base", base.toString()));
  }

  /**
   * Like {@link #byHostCount(Ipv4Network, int)}, starting from the classful network of {@code
   * address}.
   *
   * @throws IllegalArgumentException additionally if the address is in class D or E
   */
  public static Explained<SubnetPlan> byHostCount(Ipv4Address address, int requiredHosts) {
    Ipv4Network base = classfulNetwork(address);
    return planByHosts(base, requiredHosts, classfulBaseStep(address, base));
  }

  /**
   * The first {@code count} subnets of {@code base} when divided with {@code newMask}, lowest
   * first. If the base holds fewer subnets, all of them are returned and a note says so.
   *
   * @param count from 1 to 1024
   * @throws IllegalArgumentException if the mask is shorter than the base's, or the count is out of
   *     range
   */
  public static Explained<List<Ipv4Network>> listSubnets(
      Ipv4Network base, SubnetMask newMask, int count) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(newMask, "newMask");
    if (newMask.prefixLength() < base.prefixLength()) {
      throw new IllegalArgumentException(
          "the mask "
              + newMask.toCidrString()
              + " is shorter than the base network's "
              + base.mask().toCidrString()
              + ", so it cannot divide it");
    }
    if (count < 1 || count > MAX_LISTED_SUBNETS) {
      throw new IllegalArgumentException(
          "the number of subnets to list must be between 1 and "
              + MAX_LISTED_SUBNETS
              + ", was "
              + count);
    }
    long available = 1L << (newMask.prefixLength() - base.prefixLength());
    int shown = (int) Math.min(count, available);
    long size = 1L << newMask.hostBits();
    Ipv4Network first = new Ipv4Network(base.network(), newMask);

    List<ExplanationStep> steps = new ArrayList<>();
    steps.add(
        ExplanationStep.of(
            "net.list.start",
            base.toString(),
            newMask.toCidrString(),
            first.interestingOctet(),
            first.blockSize()));
    if (count > available) {
      steps.add(ExplanationStep.of("net.list.limited", count, available));
    }
    List<Ipv4Network> subnets = new ArrayList<>(shown);
    for (int i = 0; i < shown; i++) {
      Ipv4Network subnet = new Ipv4Network(base.network().plus(i * size), newMask);
      subnets.add(subnet);
      steps.add(
          ExplanationStep.of(
              "net.list.subnet",
              i + 1,
              subnet.toString(),
              subnet.firstUsable().toString(),
              subnet.lastUsable().toString(),
              subnet.broadcast().toString()));
    }
    return new Explained<>(List.copyOf(subnets), steps);
  }

  /**
   * The subnet that contains {@code host} under {@code mask}: the host ANDed with the mask, bit by
   * bit. The steps show the binary work, the broadcast address and the usable range.
   */
  public static Explained<Ipv4Network> subnetOf(Ipv4Address host, SubnetMask mask) {
    Objects.requireNonNull(host, "host");
    Objects.requireNonNull(mask, "mask");
    Ipv4Network subnet = Ipv4Network.containing(host, mask);
    int prefix = mask.prefixLength();

    List<ExplanationStep> steps = new ArrayList<>();
    steps.add(
        ExplanationStep.of(
            "net.subnet.hostBinary", host.toString(), host.toBinaryString(), prefix));
    steps.add(
        ExplanationStep.of(
            "net.subnet.maskBinary", mask.toString(), mask.toAddress().toBinaryString(), prefix));
    steps.add(
        ExplanationStep.of(
            "net.subnet.andResult",
            subnet.network().toBinaryString(),
            subnet.network().toString(),
            prefix));
    steps.add(
        ExplanationStep.of(
            "net.subnet.broadcast",
            subnet.broadcast().toBinaryString(),
            subnet.broadcast().toString(),
            mask.hostBits()));
    steps.add(
        ExplanationStep.of(
            "net.subnet.range",
            subnet.firstUsable().toString(),
            subnet.lastUsable().toString(),
            subnet.usableHostCount()));
    if (prefix == 31) {
      steps.add(ExplanationStep.of("net.subnet.pointToPoint"));
    } else if (prefix == 32) {
      steps.add(ExplanationStep.of("net.subnet.singleHost"));
    }
    steps.add(blockSizeStep("net.subnet.blockSize", subnet));
    return new Explained<>(subnet, steps);
  }

  /**
   * Subnet and host counts of {@code address} under {@code mask}, measured against the classful
   * default mask as the exams do.
   *
   * @throws IllegalArgumentException if the address is in class D or E, which have no default mask
   */
  public static Explained<ClassfulCounts> classfulCounts(Ipv4Address address, SubnetMask mask) {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(mask, "mask");
    IpClass ipClass = IpClass.of(address);
    SubnetMask defaultMask = defaultMaskOf(address, ipClass);
    int prefix = mask.prefixLength();
    int defaultPrefix = defaultMask.prefixLength();
    int hostBits = mask.hostBits();
    long hosts = Math.max(0, (1L << hostBits) - 2);

    List<ExplanationStep> steps = new ArrayList<>();
    steps.add(
        ExplanationStep.of(
            "net.classful.class",
            address.toString(),
            ipClass.name(),
            defaultPrefix,
            defaultMask.toString()));

    if (prefix < defaultPrefix) {
      int supernetBits = defaultPrefix - prefix;
      steps.add(
          ExplanationStep.of(
              "net.classful.supernet", prefix, defaultPrefix, supernetBits, 1L << supernetBits));
      steps.add(ExplanationStep.of("net.classful.hostsPerSubnet", hostBits, hosts));
      return new Explained<>(
          new ClassfulCounts(ipClass, defaultMask, mask, 0, 1, hostBits, hosts, supernetBits),
          steps);
    }

    int borrowed = prefix - defaultPrefix;
    long subnets = 1L << borrowed;
    steps.add(ExplanationStep.of("net.classful.borrowed", prefix, defaultPrefix, borrowed));
    steps.add(ExplanationStep.of("net.classful.subnetCount", borrowed, subnets));
    steps.add(ExplanationStep.of("net.classful.hostsPerSubnet", hostBits, hosts));
    if (hostBits == 1) {
      steps.add(ExplanationStep.of("net.classful.rfc3021"));
    } else if (hostBits == 0) {
      steps.add(ExplanationStep.of("net.classful.singleHost"));
    }
    steps.add(ExplanationStep.of("net.classful.subnetZeroNote"));
    return new Explained<>(
        new ClassfulCounts(ipClass, defaultMask, mask, borrowed, subnets, hostBits, hosts, 0),
        steps);
  }

  private static Explained<SubnetPlan> planBySubnets(
      Ipv4Network base, int requiredSubnets, ExplanationStep baseStep) {
    if (requiredSubnets < 1) {
      throw new IllegalArgumentException(
          "the number of subnets must be at least 1, was " + requiredSubnets);
    }
    // Smallest s with 2^s >= required, i.e. ceil(log2(required)); 0 for a single subnet.
    int borrowed = 32 - Integer.numberOfLeadingZeros(requiredSubnets - 1);
    int newPrefix = base.prefixLength() + borrowed;
    if (newPrefix > LONGEST_SUBNET_PREFIX) {
      throw new IllegalArgumentException(
          "cannot make "
              + requiredSubnets
              + " subnets from "
              + base
              + ": that needs a /"
              + newPrefix
              + " mask, but a subnet needs at least 2 host bits, so /"
              + LONGEST_SUBNET_PREFIX
              + " is the longest usable mask");
    }
    SubnetMask newMask = SubnetMask.ofPrefix(newPrefix);
    long subnets = 1L << borrowed;
    long hosts = (1L << newMask.hostBits()) - 2;
    SubnetPlan plan = new SubnetPlan(base, newMask, borrowed, subnets, hosts);

    List<ExplanationStep> steps = new ArrayList<>();
    steps.add(baseStep);
    steps.add(ExplanationStep.of("net.plan.borrowForSubnets", requiredSubnets, borrowed, subnets));
    steps.add(
        ExplanationStep.of(
            "net.plan.newMask", base.prefixLength(), borrowed, newPrefix, newMask.toString()));
    steps.add(ExplanationStep.of("net.plan.hostsPerSubnet", newMask.hostBits(), hosts));
    steps.add(blockSizeStep("net.plan.blockSize", plan.firstSubnet()));
    steps.add(ExplanationStep.of("net.plan.subnetZeroNote"));
    return new Explained<>(plan, steps);
  }

  private static Explained<SubnetPlan> planByHosts(
      Ipv4Network base, int requiredHosts, ExplanationStep baseStep) {
    if (requiredHosts < 1) {
      throw new IllegalArgumentException(
          "the number of hosts per subnet must be at least 1, was " + requiredHosts);
    }
    // Smallest h with 2^h - 2 >= required, i.e. 2^h >= required + 2. In long arithmetic because
    // required + 1 overflows an int for the largest requests.
    int hostBits = 64 - Long.numberOfLeadingZeros(requiredHosts + 1L);
    if (hostBits > base.mask().hostBits()) {
      throw new IllegalArgumentException(
          "cannot fit "
              + requiredHosts
              + " hosts per subnet into "
              + base
              + ": that needs "
              + hostBits
              + " host bits, but the base network only has "
              + base.mask().hostBits()
              + " host bits");
    }
    int newPrefix = 32 - hostBits;
    SubnetMask newMask = SubnetMask.ofPrefix(newPrefix);
    int borrowed = newPrefix - base.prefixLength();
    long subnets = 1L << borrowed;
    long hosts = (1L << hostBits) - 2;
    SubnetPlan plan = new SubnetPlan(base, newMask, borrowed, subnets, hosts);

    List<ExplanationStep> steps = new ArrayList<>();
    steps.add(baseStep);
    steps.add(ExplanationStep.of("net.plan.hostBitsNeeded", requiredHosts, hostBits, hosts));
    steps.add(
        ExplanationStep.of("net.plan.newMaskFromHosts", hostBits, newPrefix, newMask.toString()));
    steps.add(
        ExplanationStep.of(
            "net.plan.borrowedBits", base.prefixLength(), newPrefix, borrowed, subnets));
    steps.add(blockSizeStep("net.plan.blockSize", plan.firstSubnet()));
    steps.add(ExplanationStep.of("net.plan.subnetZeroNote"));
    return new Explained<>(plan, steps);
  }

  private static ExplanationStep blockSizeStep(String key, Ipv4Network network) {
    int octet = network.interestingOctet();
    return ExplanationStep.of(
        key, octet, network.mask().toAddress().octet(octet), network.blockSize());
  }

  private static Ipv4Network classfulNetwork(Ipv4Address address) {
    Objects.requireNonNull(address, "address");
    return Ipv4Network.containing(address, defaultMaskOf(address, IpClass.of(address)));
  }

  private static ExplanationStep classfulBaseStep(Ipv4Address address, Ipv4Network base) {
    return ExplanationStep.of(
        "net.plan.classfulBase",
        address.toString(),
        IpClass.of(address).name(),
        base.mask().toString(),
        base.toString());
  }

  private static SubnetMask defaultMaskOf(Ipv4Address address, IpClass ipClass) {
    return ipClass
        .defaultMask()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    address
                        + " is in class "
                        + ipClass
                        + ", which has no default mask; give a network with a prefix instead"));
  }
}
