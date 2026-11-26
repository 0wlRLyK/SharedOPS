package dev.sharedops.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.sharedops.SharedopsBackendApplication;
import dev.sharedops.household.contract.HouseholdLookup;
import dev.sharedops.household.contract.HouseholdResponsibilityAccess;
import dev.sharedops.household.contract.HouseholdTaskAccess;
import dev.sharedops.household.contract.TaskInaccessible;
import dev.sharedops.household.contract.TaskUnauthenticated;
import dev.sharedops.household.infrastructure.persistence.JdbcMembershipStore;
import dev.sharedops.responsibility.application.ResponsibilityTaskSnapshotAccess;
import dev.sharedops.responsibility.contract.ResponsibilityTaskAccess;
import dev.sharedops.responsibility.infrastructure.persistence.JdbcResponsibilityStore;
import dev.sharedops.task.application.TaskService;
import dev.sharedops.task.application.TaskStore;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class TaskBoundaryTest {
  @Test
  void exportsOnlyTheTaskFacingContractsAndRetainsHouseholdExports() {
    var modules = ApplicationModules.of(SharedopsBackendApplication.class);
    var household = modules.getModuleByName("household").orElseThrow();
    var householdTask = household.getNamedInterfaces().getByName("task").orElseThrow();
    assertThat(householdTask.contains(HouseholdTaskAccess.class)).isTrue();
    assertThat(householdTask.contains(TaskUnauthenticated.class)).isTrue();
    assertThat(householdTask.contains(TaskInaccessible.class)).isTrue();
    assertThat(householdTask.contains(JdbcMembershipStore.class)).isFalse();
    assertThat(
            household
                .getNamedInterfaces()
                .getByName("lookup")
                .orElseThrow()
                .contains(HouseholdLookup.class))
        .isTrue();
    assertThat(
            household
                .getNamedInterfaces()
                .getByName("responsibility")
                .orElseThrow()
                .contains(HouseholdResponsibilityAccess.class))
        .isTrue();

    var responsibility = modules.getModuleByName("responsibility").orElseThrow();
    var responsibilityTask = responsibility.getNamedInterfaces().getByName("task").orElseThrow();
    assertThat(responsibilityTask.contains(ResponsibilityTaskAccess.class)).isTrue();
    assertThat(responsibilityTask.contains(JdbcResponsibilityStore.class)).isFalse();
    modules.verify();
  }

  @Test
  void linkedCreationDoesNotLookUpAResponsibilityWhenHouseholdAuthorizationFails() {
    var householdId = UUID.randomUUID();
    var responsibilityId = UUID.randomUUID();
    var households = mock(HouseholdTaskAccess.class);
    var responsibilities = mock(ResponsibilityTaskAccess.class);
    var store = mock(TaskStore.class);
    var service = new TaskService(households, responsibilities, store, Clock.systemUTC());
    when(households.withLockedHousehold(eq(householdId), any()))
        .thenThrow(new HouseholdTaskAccess.Unauthenticated())
        .thenThrow(new HouseholdTaskAccess.Inaccessible());

    assertThatThrownBy(
            () ->
                service.createLinked(
                    householdId,
                    responsibilityId,
                    "Task",
                    null,
                    null,
                    null,
                    TaskService.AssignmentChoice.omitted()))
        .isInstanceOf(HouseholdTaskAccess.Unauthenticated.class);
    assertThatThrownBy(
            () ->
                service.createLinked(
                    householdId,
                    responsibilityId,
                    "Task",
                    null,
                    null,
                    null,
                    TaskService.AssignmentChoice.omitted()))
        .isInstanceOf(HouseholdTaskAccess.Inaccessible.class);
    verifyNoInteractions(responsibilities, store);
  }

  @Test
  void linkedCreationAcquiresHouseholdBeforeLookingUpResponsibility() {
    var householdId = UUID.randomUUID();
    var responsibilityId = UUID.randomUUID();
    var households = mock(HouseholdTaskAccess.class);
    var view = mock(HouseholdTaskAccess.LockedHousehold.class);
    var responsibilities = mock(ResponsibilityTaskAccess.class);
    var store = mock(TaskStore.class);
    when(households.withLockedHousehold(eq(householdId), any()))
        .thenAnswer(
            invocation -> {
              Function<HouseholdTaskAccess.LockedHousehold, ?> action = invocation.getArgument(1);
              return action.apply(view);
            });
    when(responsibilities.lockForTask(householdId, responsibilityId)).thenReturn(Optional.empty());
    var service = new TaskService(households, responsibilities, store, Clock.systemUTC());

    assertThatThrownBy(
            () ->
                service.createLinked(
                    householdId,
                    responsibilityId,
                    "Task",
                    null,
                    null,
                    null,
                    TaskService.AssignmentChoice.omitted()))
        .isInstanceOf(TaskService.NotFound.class);
    var order = inOrder(households, responsibilities);
    order.verify(households).withLockedHousehold(eq(householdId), any());
    order.verify(responsibilities).lockForTask(householdId, responsibilityId);
    verifyNoInteractions(store);
  }

  @Test
  void transactionBoundariesSurroundTheCrossModuleOperation() throws Exception {
    Method create =
        TaskService.class.getMethod(
            "createLinked",
            UUID.class,
            UUID.class,
            String.class,
            String.class,
            dev.sharedops.task.domain.TaskPriority.class,
            TaskService.DeadlineInput.class,
            TaskService.AssignmentChoice.class);
    assertThat(create.getAnnotation(Transactional.class)).isNotNull();
    Method snapshot =
        ResponsibilityTaskSnapshotAccess.class.getMethod("lockForTask", UUID.class, UUID.class);
    assertThat(snapshot.getAnnotation(Transactional.class).propagation())
        .isEqualTo(Propagation.MANDATORY);
  }
}
