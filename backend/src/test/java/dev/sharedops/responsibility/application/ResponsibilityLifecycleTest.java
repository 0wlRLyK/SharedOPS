package dev.sharedops.responsibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sharedops.household.contract.HouseholdResponsibilityAccess;
import dev.sharedops.responsibility.domain.Responsibility;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResponsibilityLifecycleTest {
  private final HouseholdResponsibilityAccess access = mock(HouseholdResponsibilityAccess.class);
  private final HouseholdResponsibilityAccess.LockedHousehold household =
      mock(HouseholdResponsibilityAccess.LockedHousehold.class);
  private final ResponsibilityStore store = mock(ResponsibilityStore.class);
  private final ResponsibilityService service = new ResponsibilityService(access, store);
  private final UUID home = UUID.randomUUID(),
      id = UUID.randomUUID(),
      owner = UUID.randomUUID(),
      account = UUID.randomUUID(),
      replacement = UUID.randomUUID();
  private final Responsibility original = Responsibility.create(id, home, "Garden", null, owner);

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    when(access.withLockedHousehold(eq(home), any()))
        .thenAnswer(
            i ->
                ((Function<HouseholdResponsibilityAccess.LockedHousehold, Object>) i.getArgument(1))
                    .apply(household));
    when(store.lock(home, id)).thenReturn(Optional.of(original));
    when(store.update(any(), anyLong())).thenReturn(true);
  }

  @Test
  void transitionsAndStalePrecedence() {
    assertThat(service.pause(home, id, 0).version()).isEqualTo(1);
    when(store.lock(home, id)).thenReturn(Optional.of(original.pause()));
    assertThat(service.resume(home, id, 1).version()).isEqualTo(2);
    assertThatThrownBy(() -> service.pause(home, id, 1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.pause(home, id, 0))
        .isInstanceOf(ResponsibilityService.VersionConflict.class);
    assertThat(service.archive(home, id, 1).version()).isEqualTo(2);
  }

  @Test
  void restoreRequiresEligibleEffectiveOwnerAndNoTransferFact() {
    Responsibility archived = original.archive();
    when(store.lock(home, id)).thenReturn(Optional.of(archived));
    assertThatThrownBy(() -> service.restore(home, id, 1, null))
        .isInstanceOf(ResponsibilityService.InvalidOwner.class);
    when(household.isActiveMembership(owner)).thenReturn(true);
    assertThat(service.restore(home, id, 1, null).ownerMembershipId()).isEqualTo(owner);
    when(household.activeMembershipId(account)).thenReturn(Optional.of(replacement));
    assertThat(service.restore(home, id, 1, account).ownerMembershipId()).isEqualTo(replacement);
    assertThatThrownBy(() -> service.restore(home, id, 0, account))
        .isInstanceOf(ResponsibilityService.VersionConflict.class);
    verify(store, never()).insertTransferFact(any());
  }

  @Test
  void failedConditionalWriteReturnsConflict() {
    when(store.update(any(), anyLong())).thenReturn(false);
    assertThatThrownBy(() -> service.archive(home, id, 0))
        .isInstanceOf(ResponsibilityService.VersionConflict.class);
  }
}
