package dev.sharedops.household.infrastructure.security;

import dev.sharedops.household.application.port.AccountPersistencePort;
import dev.sharedops.household.domain.Account;
import dev.sharedops.household.domain.utils.EmailNormalizer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {
  private final AccountPersistencePort persistencePort;

  public AccountUserDetailsService(AccountPersistencePort persistencePort) {
    this.persistencePort = persistencePort;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    Account account =
        persistencePort
            .findByEmail(EmailNormalizer.normalize(username))
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    return new AccountPrincipal(
        account.getId().id(),
        account.getEmail(),
        account.getPasswordHash(),
        account.getCredentialVersion());
  }
}
