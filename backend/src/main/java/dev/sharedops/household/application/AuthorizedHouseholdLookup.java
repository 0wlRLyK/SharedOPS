package dev.sharedops.household.application;

import dev.sharedops.household.contract.HouseholdLookup;
import dev.sharedops.household.domain.HouseholdId;
import org.springframework.stereotype.Service;

@Service
public class AuthorizedHouseholdLookup implements HouseholdLookup {
  private final CurrentAccount currentAccount;
  private final MembershipStore memberships;

  public AuthorizedHouseholdLookup(CurrentAccount currentAccount, MembershipStore memberships) {
    this.currentAccount = currentAccount;
    this.memberships = memberships;
  }

  @Override
  public boolean isAccessible(HouseholdId id) {
    if (id == null) {
      return false;
    }
    return currentAccount
        .authenticatedAccountId()
        .map(actor -> memberships.isActiveMember(actor, id.id()))
        .orElse(false);
  }
}
