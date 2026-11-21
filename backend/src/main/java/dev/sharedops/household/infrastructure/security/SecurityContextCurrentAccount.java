package dev.sharedops.household.infrastructure.security;

import dev.sharedops.household.application.AuthenticatedAccount;
import dev.sharedops.household.application.CurrentAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentAccount implements CurrentAccount {
  @Override
  public Optional<UUID> authenticatedAccountId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken
        || !(authentication.getPrincipal() instanceof AuthenticatedAccount account)) {
      return Optional.empty();
    }
    return Optional.ofNullable(account.accountId());
  }
}
