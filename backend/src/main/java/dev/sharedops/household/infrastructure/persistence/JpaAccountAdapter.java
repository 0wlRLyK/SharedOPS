package dev.sharedops.household.infrastructure.persistence;

import dev.sharedops.household.application.port.AccountPersistencePort;
import dev.sharedops.household.domain.Account;
import dev.sharedops.household.domain.AccountId;
import dev.sharedops.household.domain.exception.EmailAlreadyRegisteredException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAccountAdapter implements AccountPersistencePort {
  private final AccountRepository repository;

  public JpaAccountAdapter(AccountRepository repository) {
    this.repository = repository;
  }

  @Override
  public Account create(String email, String passwordHash) {
    AccountEntity entity = new AccountEntity(email, passwordHash);
    try {
      AccountEntity createdEntity = repository.saveAndFlush(entity);
      return new Account(
          new AccountId(createdEntity.getId()),
          createdEntity.getEmail(),
          createdEntity.getPasswordHash(),
          createdEntity.getCreatedAt());
    } catch (DataIntegrityViolationException ex) {
      Throwable cause = ex;

      while (cause != null) {
        if (cause instanceof ConstraintViolationException violation) {
          String constraintName = violation.getConstraintName();

          if ("uq_accounts_email".equals(constraintName)) {
            throw new EmailAlreadyRegisteredException(
                "An account with this email is already registered");
          }
        }

        cause = cause.getCause();
      }

      throw ex;
    }
  }

  @Override
  public Optional<Account> findByEmail(String email) {
    return repository
        .findByEmail(email)
        .map(
            account ->
                new Account(
                    new AccountId(account.getId()),
                    account.getEmail(),
                    account.getPasswordHash(),
                    account.getCreatedAt(),
                    account.getCredentialVersion()));
  }
}
