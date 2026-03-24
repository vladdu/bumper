package com.volvo.bumper;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.volvo.bumper", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  // domain must not depend on port, application, adapter, cli, or any framework
  @ArchTest
  static final ArchRule domain_must_not_depend_on_other_layers =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..port..", "..application..", "..adapter..", "..cli..");

  @ArchTest
  static final ArchRule domain_must_not_use_spring =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..");

  // application must not depend on adapter or cli
  @ArchTest
  static final ArchRule application_must_not_depend_on_adapter_or_cli =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapter..", "..cli..");

  // cli must not depend on adapter directly
  @ArchTest
  static final ArchRule cli_must_not_depend_on_adapter =
      noClasses()
          .that()
          .resideInAPackage("..cli..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapter..");

  // adapter must not depend on cli
  @ArchTest
  static final ArchRule adapter_must_not_depend_on_cli =
      noClasses()
          .that()
          .resideInAPackage("..adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..cli..");

  // port must not depend on application, adapter, or cli
  @ArchTest
  static final ArchRule port_must_not_depend_on_application_adapter_or_cli =
      noClasses()
          .that()
          .resideInAPackage("..port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application..", "..adapter..", "..cli..");

  // port must not use Spring (ports are pure interfaces)
  @ArchTest
  static final ArchRule port_must_not_use_spring =
      noClasses()
          .that()
          .resideInAPackage("..port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..");
}
