package dev.sharedops.household.application;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetHouseholdService {
  private final CurrentAccount currentAccount;
  private final MembershipStore memberships;
  private final HouseholdStore households;

  public GetHouseholdService(
      CurrentAccount currentAccount, MembershipStore memberships, HouseholdStore households) {
    this.currentAccount = currentAccount;
    this.memberships = memberships;
    this.households = households;
  }

  public HouseholdSnapshot get(UUID householdId) {
    var actor =
        currentAccount
            .authenticatedAccountId()
            .orElseThrow(UnauthenticatedHouseholdAccessException::new);
    if (householdId == null
        || memberships.findActiveHouseholdId(actor).filter(householdId::equals).isEmpty()) {
      throw new HouseholdNotFoundException();
    }
    return households.findById(householdId).orElseThrow(HouseholdNotFoundException::new);
  }
}
