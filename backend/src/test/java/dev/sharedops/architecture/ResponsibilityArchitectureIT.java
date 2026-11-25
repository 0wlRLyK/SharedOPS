package dev.sharedops.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.SharedopsBackendApplication;
import dev.sharedops.household.contract.HouseholdResponsibilityAccess;
import dev.sharedops.household.contract.ResponsibilityInaccessible;
import dev.sharedops.household.contract.ResponsibilityUnauthenticated;
import dev.sharedops.household.infrastructure.persistence.JdbcMembershipStore;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ResponsibilityArchitectureIT {
  @Test
  void responsibilityUsesOnlyTheNamedHouseholdExport() {
    var modules = ApplicationModules.of(SharedopsBackendApplication.class);
    var household = modules.getModuleByName("household").orElseThrow();
    var exported = household.getNamedInterfaces().getByName("responsibility").orElseThrow();
    assertThat(exported.contains(HouseholdResponsibilityAccess.class)).isTrue();
    assertThat(exported.contains(ResponsibilityInaccessible.class)).isTrue();
    assertThat(exported.contains(ResponsibilityUnauthenticated.class)).isTrue();
    assertThat(exported.contains(JdbcMembershipStore.class)).isFalse();
    modules.verify();
  }
}
