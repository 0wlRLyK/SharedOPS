package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.sharedops.household.domain.InvitationToken;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class InvitationServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
  private final CurrentAccount current = mock(CurrentAccount.class);
  private final MembershipStore memberships = mock(MembershipStore.class);
  private final HouseholdStore households = mock(HouseholdStore.class);
  private final InvitationStore invitations = mock(InvitationStore.class);
  private final InvitationService service =
      new InvitationService(
          current, memberships, households, invitations, Clock.fixed(NOW, ZoneOffset.UTC));
  private final UUID actor = UUID.randomUUID();
  private final UUID home = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();

  @BeforeEach
  void identity() {
    when(current.authenticatedAccountId()).thenReturn(Optional.of(actor));
  }

  @Test
  void createAuthorizesAndStoresDigestWithTwentyFourHourExpiry() {
    when(invitations.lockHousehold(home)).thenReturn(true);
    when(memberships.isActiveMember(actor, home)).thenReturn(true);
    IssuedInvitation issued = service.create(home);
    assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(86400));
    assertThat(issued.toString()).doesNotContain(issued.token());
    ArgumentCaptor<byte[]> digest = ArgumentCaptor.forClass(byte[].class);
    verify(invitations)
        .insert(eq(issued.id()), eq(home), digest.capture(), eq(NOW), eq(issued.expiresAt()));
    assertThat(digest.getValue()).isEqualTo(InvitationToken.parse(issued.token()).digest());
  }

  @Test
  void foreignMemberCannotCreateOrRevoke() {
    when(invitations.lockHousehold(home)).thenReturn(true);
    assertThatThrownBy(() -> service.create(home)).isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> service.revoke(home, id))
        .isInstanceOf(HouseholdNotFoundException.class);
    verify(invitations, never()).insert(any(), any(), any(), any(), any());
    verify(invitations, never()).revoke(any(), any());
  }

  @Test
  void revocationLocksHouseholdBeforeInvitationAndRejectsTerminalState() {
    when(invitations.lockHousehold(home)).thenReturn(true);
    when(memberships.isActiveMember(actor, home)).thenReturn(true);
    when(invitations.lockInvitation(home, id))
        .thenReturn(
            Optional.of(new InvitationStore.State(id, home, NOW.plusSeconds(1), null, null)));
    when(invitations.revoke(id, NOW)).thenReturn(true);
    service.revoke(home, id);
    InOrder order = inOrder(invitations);
    order.verify(invitations).lockHousehold(home);
    order.verify(invitations).lockInvitation(home, id);
    order.verify(invitations).revoke(id, NOW);
    when(invitations.lockInvitation(home, id))
        .thenReturn(
            Optional.of(new InvitationStore.State(id, home, NOW.plusSeconds(1), NOW, null)));
    assertThatThrownBy(() -> service.revoke(home, id))
        .isInstanceOf(InvitationConflictException.class);
  }

  @Test
  void acceptsValidInvitationAndConsumesAfterMembershipInsert() {
    InvitationToken token = InvitationToken.generate();
    when(invitations.findByDigest(any()))
        .thenReturn(Optional.of(new InvitationStore.Locator(id, home)));
    when(invitations.lockHousehold(home)).thenReturn(true);
    when(invitations.lockInvitation(home, id))
        .thenReturn(
            Optional.of(new InvitationStore.State(id, home, NOW.plusSeconds(1), null, null)));
    HouseholdSnapshot snapshot = new HouseholdSnapshot(home, "Home", null, "EUR", "Europe/Berlin");
    when(households.findById(home)).thenReturn(Optional.of(snapshot));
    when(invitations.consume(id, NOW)).thenReturn(true);
    assertThat(service.accept(token.reveal())).isEqualTo(snapshot);
    InOrder order = inOrder(invitations, memberships);
    order.verify(invitations).findByDigest(any());
    order.verify(invitations).lockHousehold(home);
    order.verify(invitations).lockInvitation(home, id);
    order.verify(memberships).insertActive(actor, home);
    order.verify(invitations).consume(id, NOW);
  }

  @Test
  void expirationDuringMembershipInsertRejectsBeforeConsumption() {
    Clock movingClock = mock(Clock.class);
    Instant expiry = NOW.plusSeconds(1);
    when(movingClock.instant()).thenReturn(NOW, expiry);
    InvitationService movingService =
        new InvitationService(current, memberships, households, invitations, movingClock);
    InvitationToken token = InvitationToken.generate();
    when(invitations.findByDigest(any()))
        .thenReturn(Optional.of(new InvitationStore.Locator(id, home)));
    when(invitations.lockHousehold(home)).thenReturn(true);
    when(invitations.lockInvitation(home, id))
        .thenReturn(Optional.of(new InvitationStore.State(id, home, expiry, null, null)));
    when(households.findById(home))
        .thenReturn(Optional.of(new HouseholdSnapshot(home, "Home", null, "EUR", "Europe/Berlin")));

    assertThatThrownBy(() -> movingService.accept(token.reveal()))
        .isInstanceOf(InvalidInvitationException.class);
    verify(memberships).insertActive(actor, home);
    verify(invitations, never()).consume(any(), any());
  }

  @Test
  void expiryBoundaryAndAlreadyMemberRejectWithoutMutation() {
    InvitationToken token = InvitationToken.generate();
    when(invitations.findByDigest(any()))
        .thenReturn(Optional.of(new InvitationStore.Locator(id, home)));
    when(invitations.lockHousehold(home)).thenReturn(true);
    when(invitations.lockInvitation(home, id))
        .thenReturn(Optional.of(new InvitationStore.State(id, home, NOW, null, null)));
    assertThatThrownBy(() -> service.accept(token.reveal()))
        .isInstanceOf(InvalidInvitationException.class);
    when(invitations.lockInvitation(home, id))
        .thenReturn(
            Optional.of(new InvitationStore.State(id, home, NOW.plusSeconds(1), null, null)));
    when(memberships.findActiveHouseholdId(actor)).thenReturn(Optional.of(UUID.randomUUID()));
    assertThatThrownBy(() -> service.accept(token.reveal()))
        .isInstanceOf(InvitationConflictException.class);
    verify(memberships, never()).insertActive(any(), any());
    verify(invitations, never()).consume(any(), any());
  }

  @Test
  void revokedConsumedUnknownAndFullHouseholdAreRejected() {
    InvitationToken token = InvitationToken.generate();
    when(invitations.findByDigest(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.accept(token.reveal()))
        .isInstanceOf(InvalidInvitationException.class);
    when(invitations.findByDigest(any()))
        .thenReturn(Optional.of(new InvitationStore.Locator(id, home)));
    when(invitations.lockHousehold(home)).thenReturn(true);
    when(invitations.lockInvitation(home, id))
        .thenReturn(
            Optional.of(new InvitationStore.State(id, home, NOW.plusSeconds(1), null, NOW)));
    assertThatThrownBy(() -> service.accept(token.reveal()))
        .isInstanceOf(InvalidInvitationException.class);
    when(invitations.lockInvitation(home, id))
        .thenReturn(
            Optional.of(new InvitationStore.State(id, home, NOW.plusSeconds(1), NOW, null)));
    assertThatThrownBy(() -> service.accept(token.reveal()))
        .isInstanceOf(InvalidInvitationException.class);
    when(invitations.lockInvitation(home, id))
        .thenReturn(
            Optional.of(new InvitationStore.State(id, home, NOW.plusSeconds(1), null, null)));
    when(invitations.activeMemberCount(home)).thenReturn(2);
    assertThatThrownBy(() -> service.accept(token.reveal()))
        .isInstanceOf(InvitationConflictException.class);
    verify(memberships, never()).insertActive(any(), any());
    verify(invitations, never()).consume(any(), any());
  }

  @Test
  void unauthenticatedCallsFailBeforeStorageAccess() {
    when(current.authenticatedAccountId()).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create(home))
        .isInstanceOf(UnauthenticatedHouseholdAccessException.class);
    assertThatThrownBy(() -> service.revoke(home, id))
        .isInstanceOf(UnauthenticatedHouseholdAccessException.class);
    assertThatThrownBy(() -> service.accept("invalid"))
        .isInstanceOf(UnauthenticatedHouseholdAccessException.class);
    verifyNoInteractions(invitations, memberships, households);
  }
}
