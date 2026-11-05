package io.github.mrramez.netlabstudio.core.model.archfixture;

import io.github.mrramez.netlabstudio.core.sim.SimAction;

/**
 * Deliberately lets a model class depend on the simulator. {@code ArchitectureTest} uses it to
 * prove that the "model does not depend on sim" rule really rejects such code. It lives only in the
 * test sources and never ships.
 */
final class ModelUsesSim {

  private ModelUsesSim() {}

  static SimAction usesSim() {
    return SimAction.DROP;
  }
}
