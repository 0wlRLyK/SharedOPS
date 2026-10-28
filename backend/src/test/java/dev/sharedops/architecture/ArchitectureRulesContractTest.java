package dev.sharedops.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import dev.sharedops.architecture.fixtures.domain.ForbiddenDomainDependency;
import dev.sharedops.household.MisplacedRepository;
import dev.sharedops.household.MisplacedSpringDataStore;
import dev.sharedops.household.MisplacedStore;
import dev.sharedops.household.api.AllowedApiToApplication;
import dev.sharedops.household.api.ApiContractFixture;
import dev.sharedops.household.api.ForbiddenApiToPersistence;
import dev.sharedops.household.application.AllowedHouseholdPersistenceUsage;
import dev.sharedops.household.application.ApplicationServiceFixture;
import dev.sharedops.household.application.ForbiddenApplicationToApi;
import dev.sharedops.household.application.ForbiddenApplicationToPersistence;
import dev.sharedops.household.domain.AllowedDomainDependency;
import dev.sharedops.household.domain.DomainTypeFixture;
import dev.sharedops.household.domain.ForbiddenDomainToApplication;
import dev.sharedops.household.infrastructure.persistence.AllowedRepository;
import dev.sharedops.household.infrastructure.persistence.HouseholdRepositoryFixture;
import dev.sharedops.task.application.ForbiddenTaskRepositoryUsage;
import org.junit.jupiter.api.Test;

public class ArchitectureRulesContractTest {
  @Test
  void rejectsRepositoriesOutsidePersistence() {
    var classes =
        new ClassFileImporter()
            .importClasses(
                MisplacedRepository.class, MisplacedSpringDataStore.class, MisplacedStore.class);
    var result = ArchitectureRulesTest.REPOSITORIES_MUST_RESIDE_IN_PERSISTENCE.evaluate(classes);

    assertThat(result.getFailureReport().getDetails())
        .anySatisfy(detail -> assertThat(detail).contains("MisplacedRepository"))
        .anySatisfy(detail -> assertThat(detail).contains("MisplacedSpringDataStore"))
        .anySatisfy(detail -> assertThat(detail).contains("MisplacedStore"));
  }

  @Test
  void allowsRepositoriesInsidePersistence() {
    var classes = new ClassFileImporter().importClasses(AllowedRepository.class);
    var result = ArchitectureRulesTest.REPOSITORIES_MUST_RESIDE_IN_PERSISTENCE.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void rejectsSpringDependenciesInDomain() {
    var classes = new ClassFileImporter().importClasses(ForbiddenDomainDependency.class);

    ArchRule rule = ArchitectureRulesTest.DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS;

    var result = rule.evaluate(classes);

    assertThatThrownBy(() -> rule.check(classes)).isInstanceOf(AssertionError.class);
    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .as("Domain must not depend on Spring ApplicationContext")
        .anySatisfy(
            detail ->
                assertThat(detail)
                    .contains(
                        "ForbiddenDomainDependency",
                        "org.springframework.context.ApplicationContext"));
  }

  @Test
  void rejectsHouseholdPersistenceInTask() {
    var classes =
        new ClassFileImporter()
            .importClasses(ForbiddenTaskRepositoryUsage.class, HouseholdRepositoryFixture.class);
    ArchRule rule = ArchitectureRulesTest.MODULE_PERSISTENCE_MUST_BE_INTERNAL;
    var result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isTrue();

    assertThat(result.getFailureReport().getDetails())
        .as("Task must not access Household persistence")
        .anySatisfy(
            detail ->
                assertThat(detail)
                    .contains("ForbiddenTaskRepositoryUsage", "HouseholdRepositoryFixture"));
  }

  @Test
  void allowsHouseholdToUseOwnPersistence() {
    var classes =
        new ClassFileImporter()
            .importClasses(
                AllowedHouseholdPersistenceUsage.class, HouseholdRepositoryFixture.class);
    ArchRule rule = ArchitectureRulesTest.MODULE_PERSISTENCE_MUST_BE_INTERNAL;
    var result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void rejectsDomainToApplicationDependency() {
    var classes =
        new ClassFileImporter()
            .importClasses(ForbiddenDomainToApplication.class, ApplicationServiceFixture.class);

    ArchRule rule = ArchitectureRulesTest.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS;

    var result = rule.evaluate(classes);
    assertThat(result.hasViolation()).isTrue();

    assertThat(result.getFailureReport().getDetails())
        .as("Domain must not depend on outer layers")
        .anySatisfy(
            detail ->
                assertThat(detail)
                    .contains("ForbiddenDomainToApplication", "ApplicationServiceFixture"));
  }

  @Test
  void allowsDependenciesWithinDomain() {
    var classes =
        new ClassFileImporter()
            .importClasses(DomainTypeFixture.class, AllowedDomainDependency.class);

    ArchRule rule = ArchitectureRulesTest.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS;
    var result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void rejectsApiToInfrastructureDependency() {
    var classes =
        new ClassFileImporter()
            .importClasses(ForbiddenApiToPersistence.class, HouseholdRepositoryFixture.class);

    ArchRule rule = ArchitectureRulesTest.API_MUST_NOT_DEPEND_ON_INFRASTRUCTURE;
    var result = rule.evaluate(classes);
    assertThat(result.hasViolation()).isTrue();

    assertThat(result.getFailureReport().getDetails())
        .as("Api must not depend on infrastructure")
        .anySatisfy(
            detail ->
                assertThat(detail)
                    .contains("ForbiddenApiToPersistence", "HouseholdRepositoryFixture"));
  }

  @Test
  void allowsApiToDependOnApplication() {
    var classes =
        new ClassFileImporter()
            .importClasses(AllowedApiToApplication.class, ApplicationServiceFixture.class);

    ArchRule rule = ArchitectureRulesTest.API_MUST_NOT_DEPEND_ON_INFRASTRUCTURE;
    var result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void rejectsApplicationToInfrastructureDependency() {
    var classes =
        new ClassFileImporter()
            .importClasses(
                ForbiddenApplicationToPersistence.class, HouseholdRepositoryFixture.class);

    ArchRule rule = ArchitectureRulesTest.APPLICATION_MUST_NOT_DEPEND_ON_OUTER_LAYERS;
    var result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isTrue();

    assertThat(result.getFailureReport().getDetails())
        .as("Application must not depend on infrastructure")
        .anySatisfy(
            detail ->
                assertThat(detail)
                    .contains("ForbiddenApplicationToPersistence", "HouseholdRepositoryFixture"));
  }

  @Test
  void rejectsApplicationToApiDependency() {
    var classes =
        new ClassFileImporter()
            .importClasses(ApiContractFixture.class, ForbiddenApplicationToApi.class);

    ArchRule rule = ArchitectureRulesTest.APPLICATION_MUST_NOT_DEPEND_ON_OUTER_LAYERS;
    var result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isTrue();

    assertThat(result.getFailureReport().getDetails())
        .as("Application must not depend on API")
        .anySatisfy(
            detail ->
                assertThat(detail).contains("ApiContractFixture", "ForbiddenApplicationToApi"));
  }

  @Test
  void allowsApplicationToDependOnDomain() {
    var classes =
        new ClassFileImporter()
            .importClasses(ApplicationServiceFixture.class, AllowedDomainDependency.class);

    ArchRule rule = ArchitectureRulesTest.APPLICATION_MUST_NOT_DEPEND_ON_OUTER_LAYERS;
    var result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }
}
