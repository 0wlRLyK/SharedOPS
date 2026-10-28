package dev.sharedops.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.SharedopsBackendApplication;
import dev.sharedops.household.contract.HouseholdLookup;
import dev.sharedops.household.domain.HouseholdId;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

  @Test
  void detectsExactlySevenBusinessModules() {
    var modules = ApplicationModules.of(SharedopsBackendApplication.class);

    assertThat(modules.stream().map(module -> module.getIdentifier().toString()).toList())
        .containsExactlyInAnyOrder(
            "household", "responsibility", "task", "list", "activity", "finance", "overview");
  }

  @Test
  void verifiesModuleBoundaries() {
    ApplicationModules.of(SharedopsBackendApplication.class).verify();
  }

  @Test
  void exportsHouseholdLookupContract() {
    var modules = ApplicationModules.of(SharedopsBackendApplication.class);

    var household = modules.getModuleByName("household").orElseThrow();

    var lookupInterface = household.getNamedInterfaces().getByName("lookup").orElseThrow();
    assertThat(lookupInterface.contains(HouseholdLookup.class)).isTrue();
    assertThat(lookupInterface.contains(HouseholdId.class)).isTrue();
  }
}
