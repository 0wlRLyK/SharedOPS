package dev.sharedops.household.application.port;

import dev.sharedops.household.domain.Account;
import java.util.Optional;

public interface AccountPersistencePort {
  Account create(String email, String passwordHash);

  Optional<Account> findByEmail(String email);
}
