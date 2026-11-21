package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CreateHouseholdContractTest {
  @Test
  void publicCreationEntryExists() {
    assertThatCode(
            () ->
                Class.forName("dev.sharedops.household.application.CreateHouseholdService")
                    .getMethod("create", HouseholdDraft.class))
        .doesNotThrowAnyException();
  }
}
