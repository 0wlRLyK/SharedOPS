package dev.sharedops.household.application;

import org.springframework.stereotype.Service;

@Service
public class CurrentContextService {
  private final CurrentAccount currentAccount;
  private final CurrentContextStore store;

  public CurrentContextService(CurrentAccount currentAccount, CurrentContextStore store) {
    this.currentAccount = currentAccount;
    this.store = store;
  }

  public CurrentContext get() {
    var accountId =
        currentAccount
            .authenticatedAccountId()
            .orElseThrow(UnauthenticatedHouseholdAccessException::new);
    return store
        .findForAccount(accountId)
        .orElseThrow(UnauthenticatedHouseholdAccessException::new);
  }
}
