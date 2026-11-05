package io.github.mrramez.netlabstudio;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Layering rules from CLAUDE.md, rule 3: the {@code core} packages hold all logic and stay free of
 * any UI code, so they can be unit-tested without a screen.
 */
class ArchitectureTest {

  private static final String ROOT = "io.github.mrramez.netlabstudio";
  private static final String CORE = ROOT + ".core..";

  // The rules below are not vacuous: core.net has classes, and the *RejectsAViolation tests feed
  // them code that breaks each rule. Rules for a package that is still empty (sim, cli, ...) must
  // add allowEmptyShould(true) until it gets classes.
  static final ArchRule CORE_DOES_NOT_USE_JAVAFX =
      noClasses()
          .that()
          .resideInAPackage(CORE)
          .should()
          .dependOnClassesThat()
          .resideInAPackage("javafx..")
          .because("core holds all logic and must be testable without a UI toolkit");

  // A core class could otherwise reach JavaFX indirectly, through a ui or app class.
  static final ArchRule CORE_DOES_NOT_DEPEND_ON_UI_OR_APP =
      noClasses()
          .that()
          .resideInAPackage(CORE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ROOT + ".ui..", ROOT + ".app..")
          .because("dependencies point from ui and app to core, never back");

  private static final String CORE_NET = ROOT + ".core.net..";

  /** Classes in {@code packageName} may depend only on the JDK and on that package itself. */
  static ArchRule onlyJdkAndItself(String packageName) {
    return classes()
        .that()
        .resideInAPackage(packageName)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", packageName)
        .because("addressing arithmetic is pure Java and must not pull in any other code");
  }

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ROOT);

  private static final JavaClasses VIOLATING_FIXTURE =
      new ClassFileImporter().importPackages(ROOT + ".core.archfixture");

  @Test
  void coreDoesNotUseJavaFx() {
    CORE_DOES_NOT_USE_JAVAFX.check(PRODUCTION_CLASSES);
  }

  @Test
  void coreDoesNotDependOnUiOrApp() {
    CORE_DOES_NOT_DEPEND_ON_UI_OR_APP.check(PRODUCTION_CLASSES);
  }

  @Test
  void productionClassesAreActuallyScanned() {
    assertThat(PRODUCTION_CLASSES.containPackage(ROOT + ".ui")).isTrue();
  }

  @Test
  void javaFxRuleRejectsAViolation() {
    assertThat(CORE_DOES_NOT_USE_JAVAFX.evaluate(VIOLATING_FIXTURE).hasViolation()).isTrue();
  }

  @Test
  void uiRuleRejectsAViolation() {
    assertThat(CORE_DOES_NOT_DEPEND_ON_UI_OR_APP.evaluate(VIOLATING_FIXTURE).hasViolation())
        .isTrue();
  }

  @Test
  void coreNetDependsOnlyOnTheJdkAndItself() {
    onlyJdkAndItself(CORE_NET).check(PRODUCTION_CLASSES);
  }

  @Test
  void coreNetIsActuallyScanned() {
    assertThat(PRODUCTION_CLASSES.containPackage(ROOT + ".core.net")).isTrue();
    assertThat(PRODUCTION_CLASSES.that(resideInAPackage(CORE_NET))).isNotEmpty();
  }

  @Test
  void jdkOnlyRuleRejectsAViolation() {
    assertThat(
            onlyJdkAndItself(ROOT + ".core.archfixture").evaluate(VIOLATING_FIXTURE).hasViolation())
        .isTrue();
  }
}
