package dev.sharedops.household.application;

import dev.sharedops.household.application.port.AccountPersistencePort;
import dev.sharedops.household.domain.Account;
import dev.sharedops.household.domain.utils.EmailNormalizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterAccountService {
  private final AccountPersistencePort persistencePort;
  private final PasswordEncoder passwordEncoder;

  public RegisterAccountService(
      AccountPersistencePort persistencePort, PasswordEncoder passwordEncoder) {
    this.persistencePort = persistencePort;
    this.passwordEncoder = passwordEncoder;
  }

  public Account register(String email, String rawPassword) {
    String normalizedEmail = EmailNormalizer.normalize(email);
    return persistencePort.create(normalizedEmail, passwordEncoder.encode(rawPassword));
  }
}
