package dev.sharedops.responsibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sharedops.household.contract.HouseholdResponsibilityAccess;
import dev.sharedops.responsibility.domain.Responsibility;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResponsibilityCreateReadTest {
  HouseholdResponsibilityAccess households = mock(HouseholdResponsibilityAccess.class);
  HouseholdResponsibilityAccess.LockedHousehold locked =
      mock(HouseholdResponsibilityAccess.LockedHousehold.class);
  ResponsibilityStore store = mock(ResponsibilityStore.class);
  ResponsibilityService service = new ResponsibilityService(households, store);
  UUID home = UUID.randomUUID();
  UUID account = UUID.randomUUID();
  UUID membership = UUID.randomUUID();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void authorize() {
    when(households.withLockedHousehold(eq(home), any()))
        .thenAnswer(
            invocation ->
                ((Function<HouseholdResponsibilityAccess.LockedHousehold, Object>)
                        invocation.getArgument(1))
                    .apply(locked));
  }

  @Test
  void createResolvesActiveOwnerAndPersistsVersionZero() {
    when(locked.activeMembershipId(account)).thenReturn(Optional.of(membership));
    Responsibility created = service.create(home, "Garden", "", account);
    assertThat(created.ownerMembershipId()).isEqualTo(membership);
    assertThat(created.version()).isZero();
    assertThat(created.description()).isEmpty();
    verify(store).insert(created);
  }

  @Test
  void rejectsInvalidOwnerAndBlankTitleWithoutInsert() {
    when(locked.activeMembershipId(account)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create(home, "Title", null, account))
        .isInstanceOf(ResponsibilityService.InvalidOwner.class);
    assertThatThrownBy(() -> service.create(home, "Title", null, null))
        .isInstanceOf(ResponsibilityService.InvalidOwner.class);
    when(locked.activeMembershipId(account)).thenReturn(Optional.of(membership));
    assertThatThrownBy(() -> service.create(home, "  ", null, account))
        .isInstanceOf(IllegalArgumentException.class);
    verify(store, never()).insert(any());
  }

  @Test
  void delegatesQueriesOnlyAfterAuthorizationAndReportsMissingUniformly() {
    UUID id = UUID.randomUUID();
    when(store.find(home, id)).thenReturn(Optional.empty());
    when(store.list(home, true)).thenReturn(List.of());
    assertThatThrownBy(() -> service.get(home, id))
        .isInstanceOf(ResponsibilityService.NotFound.class);
    assertThat(service.list(home, true)).isEmpty();
    verify(store).find(home, id);
    verify(store).list(home, true);
  }
}
