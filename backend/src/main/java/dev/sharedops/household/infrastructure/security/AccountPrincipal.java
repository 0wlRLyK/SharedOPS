package dev.sharedops.household.infrastructure.security;

import dev.sharedops.household.application.AuthenticatedAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class AccountPrincipal extends User implements AuthenticatedAccount {

  private static final long serialVersionUID = 1L;

  private final UUID accountId;
  private final Long credentialVersion;

  public AccountPrincipal(
      UUID accountId, String email, String passwordHash, long credentialVersion) {
    super(email, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_USER")));

    this.accountId = accountId;
    this.credentialVersion = credentialVersion;
  }

  public Long credentialVersion() {
    return credentialVersion;
  }

  public UUID accountId() {
    return accountId;
  }
}
