package io.github.mrramez.netlabstudio.core.model;

/** The state of a link, shown as the colour of the cable on the canvas. */
public enum LinkState {
  /** Both ends are enabled and the cable is correct (green). */
  UP,
  /** No cable, or a cable that cannot work between these two ports (red). */
  DOWN,
  /** The cable is fine but an end was switched off by configuration (amber). */
  ADMIN_DOWN
}
