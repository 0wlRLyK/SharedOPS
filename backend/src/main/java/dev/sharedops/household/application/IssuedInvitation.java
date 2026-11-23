package dev.sharedops.household.application;

import java.time.Instant;
import java.util.UUID;

/** The secret is exposed only to the issuing caller and never by string representation. */
public final class IssuedInvitation {
  private final UUID id;
  private final String token;
  private final Instant expiresAt;

  public IssuedInvitation(UUID id, String token, Instant expiresAt) {
    this.id = id;
    this.token = token;
    this.expiresAt = expiresAt;
  }

  public UUID id() {
    return id;
  }

  public String token() {
    return token;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  @Override
  public String toString() {
    return "IssuedInvitation[id=" + id + ", token=REDACTED, expiresAt=" + expiresAt + "]";
  }
}
