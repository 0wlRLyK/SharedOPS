package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.household.contract.HouseholdLookup;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HouseholdLookupContractTest {
  @Test
  void exportedContractDoesNotExposeIdentityFreeExistenceLookup() {
    assertThat(Arrays.stream(HouseholdLookup.class.getDeclaredMethods()).map(m -> m.getName()))
        .containsExactly("isAccessible");
  }
}
