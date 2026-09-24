/**
 * Deterministic discrete-event simulation of frames, ARP, IPv4 and ICMP moving through a topology,
 * with each device's behaviour. It emits simulation events that the UI replays step by step.
 *
 * <p>There is no wall-clock timing and MAC generation is seeded, so tests can assert exact event
 * sequences.
 */
package io.github.mrramez.netlabstudio.core.sim;
