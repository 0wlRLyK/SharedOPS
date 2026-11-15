package dev.sharedops.household.domain;

import java.time.Instant;

public class Account {
  private AccountId id;
  private String email;
  private String passwordHash;
  private Instant createdAt;
  private long credentialVersion;

  public Account(AccountId id, String email, String passwordHash, Instant createdAt) {
    this(id, email, passwordHash, createdAt, 0);
  }

  public Account(
      AccountId id, String email, String passwordHash, Instant createdAt, long credentialVersion) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
    this.credentialVersion = credentialVersion;
  }

  public long getCredentialVersion() {
    return credentialVersion;
  }

  public AccountId getId() {
    return id;
  }

  public void setId(AccountId id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
