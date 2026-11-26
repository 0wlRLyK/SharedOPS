package dev.sharedops.household.application;

import dev.sharedops.household.contract.HouseholdTaskAccess;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Provides task operations a scoped view under the household serialization lock. */
@Service
public class TaskHouseholdAccess implements HouseholdTaskAccess {
  private final CurrentAccount currentAccount;
  private final InvitationStore locks;
  private final HouseholdStore households;
  private final MembershipStore memberships;
  private final ResponsibilityMembershipStore memberLookup;

  public TaskHouseholdAccess(
      CurrentAccount currentAccount,
      InvitationStore locks,
      HouseholdStore households,
      MembershipStore memberships,
      ResponsibilityMembershipStore memberLookup) {
    this.currentAccount = currentAccount;
    this.locks = locks;
    this.households = households;
    this.memberships = memberships;
    this.memberLookup = memberLookup;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public <T> T withLockedHousehold(UUID householdId, Function<LockedHousehold, T> action) {
    UUID actor = currentAccount.authenticatedAccountId().orElseThrow(Unauthenticated::new);
    if (householdId == null
        || !locks.lockHousehold(householdId)
        || !memberships.isActiveMember(actor, householdId)) {
      throw new Inaccessible();
    }
    var household = households.findById(householdId).orElseThrow(Inaccessible::new);
    var view = new LockedView(actor, householdId, household.timezone());
    try {
      return action.apply(view);
    } finally {
      view.open = false;
    }
  }

  private final class LockedView implements LockedHousehold {
    private final UUID actor;
    private final UUID householdId;
    private final String timezone;
    private boolean open = true;

    private LockedView(UUID actor, UUID householdId, String timezone) {
      this.actor = actor;
      this.householdId = householdId;
      this.timezone = timezone;
    }

    @Override
    public UUID actorAccountId() {
      requireOpen();
      return actor;
    }

    @Override
    public String timezone() {
      requireOpen();
      return timezone;
    }

    @Override
    public Optional<UUID> activeMembershipId(UUID accountId) {
      requireOpen();
      return accountId == null
          ? Optional.empty()
          : memberLookup.findActiveMembershipId(accountId, householdId);
    }

    @Override
    public boolean isActiveMembership(UUID membershipId) {
      requireOpen();
      return membershipId != null && memberLookup.isActiveMembership(membershipId, householdId);
    }

    @Override
    public Optional<UUID> historicalAccountId(UUID membershipId) {
      requireOpen();
      return membershipId == null
          ? Optional.empty()
          : memberLookup.findAccountId(membershipId, householdId);
    }

    private void requireOpen() {
      if (!open) {
        throw new IllegalStateException("Household view is no longer valid");
      }
    }
  }
}
