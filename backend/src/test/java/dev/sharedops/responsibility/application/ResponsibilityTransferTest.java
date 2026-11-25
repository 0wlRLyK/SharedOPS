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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResponsibilityTransferTest {
  private final HouseholdResponsibilityAccess access = mock(HouseholdResponsibilityAccess.class);
  private final HouseholdResponsibilityAccess.LockedHousehold household =
      mock(HouseholdResponsibilityAccess.LockedHousehold.class);
  private final ResponsibilityStore store = mock(ResponsibilityStore.class);
  private final Instant now = Instant.parse("2026-01-01T10:00:00Z");
  private final ResponsibilityService service =
      new ResponsibilityService(access, store, Clock.fixed(now, ZoneOffset.UTC));
  private final UUID home = UUID.randomUUID(),
      id = UUID.randomUUID(),
      owner = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      account = UUID.randomUUID(),
      newOwner = UUID.randomUUID();
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
    when(household.activeMembershipId(account)).thenReturn(Optional.of(newOwner));
    when(household.actorAccountId()).thenReturn(actor);
    when(store.update(any(), eq(0L))).thenReturn(true);
  }

  @Test
  void transferUpdatesOnceAndRecordsActorTimeAndOwners() {
    Responsibility result = service.transfer(home, id, 0, account);
    assertThat(result.ownerMembershipId()).isEqualTo(newOwner);
    assertThat(result.version()).isEqualTo(1);
    var fact = ArgumentCaptor.forClass(ResponsibilityStore.TransferFact.class);
    verify(store).update(result, 0);
    verify(store).insertTransferFact(fact.capture());
    assertThat(fact.getValue().actorAccountId()).isEqualTo(actor);
    assertThat(fact.getValue().occurredAt()).isEqualTo(now);
    assertThat(fact.getValue().previousOwnerMembershipId()).isEqualTo(owner);
    assertThat(fact.getValue().newOwnerMembershipId()).isEqualTo(newOwner);
  }

  @Test
  void staleComesBeforeOwnerValidationAndNoop() {
    assertThatThrownBy(() -> service.transfer(home, id, 2, account))
        .isInstanceOf(ResponsibilityService.VersionConflict.class);
    verify(household, never()).activeMembershipId(any());
    verify(store, never()).update(any(), anyLong());
    verify(store, never()).insertTransferFact(any());
  }

  @Test
  void noopsAndInvalidOwnerNeverWrite() {
    when(household.activeMembershipId(account)).thenReturn(Optional.of(owner));
    assertThat(service.transfer(home, id, 0, account)).isSameAs(original);
    assertThat(service.edit(home, id, 0, "Garden", null)).isSameAs(original);
    when(household.activeMembershipId(account)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.transfer(home, id, 0, account))
        .isInstanceOf(ResponsibilityService.InvalidOwner.class);
    verify(store, never()).update(any(), anyLong());
    verify(store, never()).insertTransferFact(any());
  }

  @Test
  void conditionalWriteFailureRejectsAndDoesNotInsertFact() {
    when(store.update(any(), eq(0L))).thenReturn(false);
    assertThatThrownBy(() -> service.transfer(home, id, 0, account))
        .isInstanceOf(ResponsibilityService.VersionConflict.class);
    verify(store, never()).insertTransferFact(any());
  }

  @Test
  void editsAdvanceAndArchivedChangesReject() {
    assertThat(service.edit(home, id, 0, "Plants", "text").version()).isEqualTo(1);
    when(store.lock(home, id)).thenReturn(Optional.of(original.archive()));
    assertThatThrownBy(() -> service.edit(home, id, 1, "Plants", null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.transfer(home, id, 1, account))
        .isInstanceOf(IllegalStateException.class);
    verify(store, never()).insertTransferFact(any());
  }
}
