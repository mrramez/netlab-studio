package io.github.mrramez.netlabstudio.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExplanationStepTest {

  @Test
  void keepsKeyAndArguments() {
    ExplanationStep step = ExplanationStep.of("net.subnet.borrowBits", 4, "10.0.0.0", 12L, true);

    assertThat(step.key()).isEqualTo("net.subnet.borrowBits");
    assertThat(step.args()).containsExactly(4, "10.0.0.0", 12L, true);
  }

  @Test
  void aStepWithoutArgumentsIsAllowed() {
    assertThat(ExplanationStep.of("net.subnet.supernet").args()).isEmpty();
  }

  @Test
  void copiesTheArgumentListDefensively() {
    List<Object> source = new ArrayList<>(List.of("a"));
    ExplanationStep step = new ExplanationStep("net.x.y", source);

    source.add("b");

    assertThat(step.args()).containsExactly("a");
    assertThatThrownBy(() -> step.args().add("c"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "", " ", "net", "Net.x", "net..x", "net.x.", ".net.x", "net.x y", "net.1x", "net.x-y"
      })
  void rejectsAMalformedKey(String key) {
    assertThatThrownBy(() -> ExplanationStep.of(key))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("key");
  }

  @Test
  void rejectsMutableOrUnsupportedArguments() {
    assertThatThrownBy(() -> ExplanationStep.of("net.x.y", new StringBuilder("a")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("StringBuilder");
    assertThatThrownBy(() -> ExplanationStep.of("net.x.y", 1.5d))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> ExplanationStep.of(null));
    assertThatNullPointerException().isThrownBy(() -> new ExplanationStep("net.x.y", null));
    assertThatNullPointerException().isThrownBy(() -> ExplanationStep.of("net.x.y", (Object) null));
  }

  @Test
  void explainedPairsAResultWithItsSteps() {
    List<ExplanationStep> steps = new ArrayList<>(List.of(ExplanationStep.of("net.x.y", 1)));
    Explained<String> explained = new Explained<>("answer", steps);

    steps.clear();

    assertThat(explained.result()).isEqualTo("answer");
    assertThat(explained.steps()).hasSize(1);
    assertThatThrownBy(() -> explained.steps().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void explainedRejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> new Explained<>(null, List.of()));
    assertThatNullPointerException().isThrownBy(() -> new Explained<>("x", null));
  }
}
