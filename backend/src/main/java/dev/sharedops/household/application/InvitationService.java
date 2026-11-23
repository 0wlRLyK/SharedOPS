package dev.sharedops.household.application;

import dev.sharedops.household.domain.InvitationToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional application boundary for invitation lifecycle operations. */
@Service
public class InvitationService {
  private static final Duration LIFETIME = Duration.ofHours(24);
  private final CurrentAccount currentAccount;
  private final MembershipStore memberships;
  private final HouseholdStore households;
  private final InvitationStore invitations;
  private final Clock clock;

  public InvitationService(
      CurrentAccount currentAccount,
      MembershipStore memberships,
      HouseholdStore households,
      InvitationStore invitations,
      Clock clock) {
    this.currentAccount = currentAccount;
    this.memberships = memberships;
    this.households = households;
    this.invitations = invitations;
    this.clock = clock;
  }

  @Transactional
  public IssuedInvitation create(UUID householdId) {
    UUID actor = actor();
    authorizeLocked(actor, householdId);
    // PostgreSQL stores timestamps at microsecond precision. Use that same instant
    // for persistence and the returned deadline so the two cannot diverge.
    Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Instant expiresAt = createdAt.plus(LIFETIME);
    InvitationToken token = InvitationToken.generate();
    UUID id = UUID.randomUUID();
    invitations.insert(id, householdId, token.digest(), createdAt, expiresAt);
    return new IssuedInvitation(id, token.reveal(), expiresAt);
  }

  @Transactional
  public void revoke(UUID householdId, UUID invitationId) {
    UUID actor = actor();
    authorizeLocked(actor, householdId);
    if (invitationId == null) {
      throw new HouseholdNotFoundException();
    }
    InvitationStore.State state =
        invitations
            .lockInvitation(householdId, invitationId)
            .orElseThrow(HouseholdNotFoundException::new);
    if (state.consumedAt() != null || state.revokedAt() != null) {
      throw new InvitationConflictException();
    }
    if (!invitations.revoke(state.id(), clock.instant())) {
      throw new InvitationConflictException();
    }
  }

  @Transactional
  public HouseholdSnapshot accept(String rawToken) {
    UUID actor = actor();
    InvitationToken token = InvitationToken.parse(rawToken);
    InvitationStore.Locator locator =
        invitations.findByDigest(token.digest()).orElseThrow(InvalidInvitationException::new);
    if (!invitations.lockHousehold(locator.householdId())) {
      throw new InvalidInvitationException();
    }
    InvitationStore.State state =
        invitations
            .lockInvitation(locator.householdId(), locator.invitationId())
            .orElseThrow(InvalidInvitationException::new);
    Instant now = clock.instant();
    if (state.consumedAt() != null
        || state.revokedAt() != null
        || !now.isBefore(state.expiresAt())) {
      throw new InvalidInvitationException();
    }
    if (memberships.findActiveHouseholdId(actor).isPresent()
        || invitations.activeMemberCount(locator.householdId()) >= 2) {
      throw new InvitationConflictException();
    }
    HouseholdSnapshot household =
        households.findById(locator.householdId()).orElseThrow(InvalidInvitationException::new);
    memberships.insertActive(actor, locator.householdId());
    // The membership insert can wait on another transaction's active-account constraint.
    // Re-read time after that wait so a late acceptance rolls the membership back.
    Instant consumptionTime = clock.instant();
    if (!consumptionTime.isBefore(state.expiresAt())) {
      throw new InvalidInvitationException();
    }
    if (!invitations.consume(state.id(), consumptionTime)) {
      throw new InvalidInvitationException();
    }
    return household;
  }

  private UUID actor() {
    return currentAccount
        .authenticatedAccountId()
        .orElseThrow(UnauthenticatedHouseholdAccessException::new);
  }

  private void authorizeLocked(UUID actor, UUID householdId) {
    if (householdId == null
        || !invitations.lockHousehold(householdId)
        || !memberships.isActiveMember(actor, householdId)) {
      throw new HouseholdNotFoundException();
    }
  }
}
