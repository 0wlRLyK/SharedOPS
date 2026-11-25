package dev.sharedops.household.application;

import dev.sharedops.household.contract.HouseholdResponsibilityAccess;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Authorizes and resolves owners while holding the shared household serialization lock. */
@Service
public class ResponsibilityHouseholdAccess implements HouseholdResponsibilityAccess {
  private final CurrentAccount currentAccount;
  private final InvitationStore households;
  private final MembershipStore memberships;
  private final ResponsibilityMembershipStore responsibilityMemberships;

  public ResponsibilityHouseholdAccess(
      CurrentAccount currentAccount,
      InvitationStore households,
      MembershipStore memberships,
      ResponsibilityMembershipStore responsibilityMemberships) {
    this.currentAccount = currentAccount;
    this.households = households;
    this.memberships = memberships;
    this.responsibilityMemberships = responsibilityMemberships;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public <T> T withLockedHousehold(UUID householdId, Function<LockedHousehold, T> action) {
    UUID actor = currentAccount.authenticatedAccountId().orElseThrow(Unauthenticated::new);
    if (householdId == null
        || !households.lockHousehold(householdId)
        || !memberships.isActiveMember(actor, householdId)) {
      throw new Inaccessible();
    }
    var view = new LockedView(actor, householdId);
    try {
      return action.apply(view);
    } finally {
      view.open = false;
    }
  }

  private final class LockedView implements LockedHousehold {
    private final UUID actor;
    private final UUID householdId;
    private boolean open = true;

    private LockedView(UUID actor, UUID householdId) {
      this.actor = actor;
      this.householdId = householdId;
    }

    @Override
    public UUID actorAccountId() {
      requireOpen();
      return actor;
    }

    @Override
    public Optional<UUID> activeMembershipId(UUID accountId) {
      requireOpen();
      return accountId == null
          ? Optional.empty()
          : responsibilityMemberships.findActiveMembershipId(accountId, householdId);
    }

    @Override
    public Optional<UUID> historicalAccountId(UUID membershipId) {
      requireOpen();
      return membershipId == null
          ? Optional.empty()
          : responsibilityMemberships.findAccountId(membershipId, householdId);
    }

    @Override
    public boolean isActiveMembership(UUID membershipId) {
      requireOpen();
      return membershipId != null
          && responsibilityMemberships.isActiveMembership(membershipId, householdId);
    }

    private void requireOpen() {
      if (!open) {
        throw new IllegalStateException("Household view is no longer valid");
      }
    }
  }
}
