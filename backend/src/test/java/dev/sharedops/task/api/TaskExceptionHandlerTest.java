package dev.sharedops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.common.api.ApiProblemFactory;
import dev.sharedops.household.contract.HouseholdTaskAccess;
import org.junit.jupiter.api.Test;

class TaskExceptionHandlerTest {
  private final TaskExceptionHandler handler = new TaskExceptionHandler(new ApiProblemFactory());

  @Test
  void householdFailuresRetainTheirNonDisclosingHttpMappings() {
    var unauthenticated = handler.unauthenticated(new HouseholdTaskAccess.Unauthenticated());
    assertThat(unauthenticated.getStatus()).isEqualTo(401);
    assertThat(unauthenticated.getProperties()).containsEntry("code", "UNAUTHORIZED");

    var inaccessible = handler.notFound(new HouseholdTaskAccess.Inaccessible());
    assertThat(inaccessible.getStatus()).isEqualTo(404);
    assertThat(inaccessible.getProperties()).containsEntry("code", "NOT_FOUND");
  }
}
