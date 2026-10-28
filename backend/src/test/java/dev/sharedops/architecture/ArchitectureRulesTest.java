package dev.sharedops.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

@AnalyzeClasses(packages = "dev.sharedops", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS =
      noClasses()
          .that()
          .resideInAPackage("dev.sharedops..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta..",
              "org.hibernate..",
              "com.fasterxml.jackson..",
              "tools.jackson..",
              "java.net.http..")
          .because("business domain logic must remain framework-independent")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule MODULE_PERSISTENCE_MUST_BE_INTERNAL =
      CompositeArchRule.of(persistenceInternalTo("household"))
          .and(persistenceInternalTo("responsibility"))
          .and(persistenceInternalTo("task"))
          .and(persistenceInternalTo("overview"))
          .and(persistenceInternalTo("list"))
          .and(persistenceInternalTo("activity"))
          .and(persistenceInternalTo("finance"));

  @ArchTest
  static final ArchRule REPOSITORIES_MUST_RESIDE_IN_PERSISTENCE =
      classes()
          .that()
          .resideInAnyPackage(
              "dev.sharedops.household..",
              "dev.sharedops.responsibility..",
              "dev.sharedops.task..",
              "dev.sharedops.list..",
              "dev.sharedops.activity..",
              "dev.sharedops.finance..",
              "dev.sharedops.overview..")
          .and(
              new DescribedPredicate<JavaClass>("are repositories") {
                @Override
                public boolean test(JavaClass type) {
                  return type.getSimpleName().endsWith("Repository")
                      || type.isAnnotatedWith(org.springframework.stereotype.Repository.class)
                      || type.isAssignableTo(org.springframework.data.repository.Repository.class);
                }
              })
          .should()
          .resideInAnyPackage(
              "dev.sharedops.household.infrastructure.persistence..",
              "dev.sharedops.responsibility.infrastructure.persistence..",
              "dev.sharedops.task.infrastructure.persistence..",
              "dev.sharedops.list.infrastructure.persistence..",
              "dev.sharedops.activity.infrastructure.persistence..",
              "dev.sharedops.finance.infrastructure.persistence..",
              "dev.sharedops.overview.infrastructure.persistence..")
          .because("module repositories must be internal to persistence")
          .allowEmptyShould(true);

  private static ArchRule persistenceInternalTo(String module) {
    String basePackage = "dev.sharedops." + module;

    return noClasses()
        .that()
        .resideOutsideOfPackage(basePackage + "..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(basePackage + ".infrastructure.persistence..")
        .because("module persistence must remain internal")
        .allowEmptyShould(true);
  }

  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("dev.sharedops..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.sharedops..api..",
              "dev.sharedops..infrastructure..",
              "dev.sharedops..application..")
          .because("business domain logic must not depend on outer layers")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule API_MUST_NOT_DEPEND_ON_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("dev.sharedops..api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.sharedops..infrastructure..")
          .because("business domain logic must not depend on outer layers")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule APPLICATION_MUST_NOT_DEPEND_ON_OUTER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("dev.sharedops..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.sharedops..api..", "dev.sharedops..infrastructure..")
          .because("application layer must not depend on outer layers")
          .allowEmptyShould(true);
}
