package dev.sharedops.responsibility.domain;

import java.util.Objects;
import java.util.UUID;

/** Immutable responsibility state; the application validates membership and expected versions. */
public record Responsibility(
    UUID id,
    UUID householdId,
    String title,
    String description,
    ResponsibilityMode mode,
    ResponsibilityStatus status,
    UUID ownerMembershipId,
    long version) {

  public Responsibility {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(householdId, "householdId");
    requireTitle(title);
    requireDescription(description);
    Objects.requireNonNull(mode, "mode");
    if (mode != ResponsibilityMode.INDIVIDUAL) {
      throw new IllegalArgumentException("Only individual responsibilities are supported");
    }
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(ownerMembershipId, "ownerMembershipId");
    if (version < 0) {
      throw new IllegalArgumentException("Version must be nonnegative");
    }
  }

  public static Responsibility create(
      UUID id, UUID householdId, String title, String description, UUID ownerMembershipId) {
    return new Responsibility(
        id,
        householdId,
        title,
        description,
        ResponsibilityMode.INDIVIDUAL,
        ResponsibilityStatus.ACTIVE,
        ownerMembershipId,
        0);
  }

  public static Responsibility rehydrate(
      UUID id,
      UUID householdId,
      String title,
      String description,
      ResponsibilityMode mode,
      ResponsibilityStatus status,
      UUID ownerMembershipId,
      long version) {
    return new Responsibility(
        id, householdId, title, description, mode, status, ownerMembershipId, version);
  }

  public Responsibility edit(String newTitle, String newDescription) {
    requireCurrent();
    requireTitle(newTitle);
    requireDescription(newDescription);
    if (title.equals(newTitle) && Objects.equals(description, newDescription)) {
      return this;
    }
    return changed(newTitle, newDescription, status, ownerMembershipId);
  }

  public Responsibility transfer(UUID newOwnerMembershipId) {
    requireCurrent();
    Objects.requireNonNull(newOwnerMembershipId, "newOwnerMembershipId");
    if (ownerMembershipId.equals(newOwnerMembershipId)) {
      return this;
    }
    return changed(title, description, status, newOwnerMembershipId);
  }

  public Responsibility pause() {
    requireStatus(ResponsibilityStatus.ACTIVE);
    return changed(title, description, ResponsibilityStatus.PAUSED, ownerMembershipId);
  }

  public Responsibility resume() {
    requireStatus(ResponsibilityStatus.PAUSED);
    return changed(title, description, ResponsibilityStatus.ACTIVE, ownerMembershipId);
  }

  public Responsibility archive() {
    requireCurrent();
    return changed(title, description, ResponsibilityStatus.ARCHIVED, ownerMembershipId);
  }

  /** Caller resolves an active same-household owner before restoring. */
  public Responsibility restore(UUID activeOwnerMembershipId) {
    requireStatus(ResponsibilityStatus.ARCHIVED);
    Objects.requireNonNull(activeOwnerMembershipId, "activeOwnerMembershipId");
    return changed(title, description, ResponsibilityStatus.ACTIVE, activeOwnerMembershipId);
  }

  private Responsibility changed(
      String nextTitle, String nextDescription, ResponsibilityStatus nextStatus, UUID nextOwner) {
    return new Responsibility(
        id,
        householdId,
        nextTitle,
        nextDescription,
        mode,
        nextStatus,
        nextOwner,
        Math.incrementExact(version));
  }

  private void requireCurrent() {
    if (status == ResponsibilityStatus.ARCHIVED) {
      throw new InvalidTransition("Archived responsibilities cannot be changed");
    }
  }

  private void requireStatus(ResponsibilityStatus expected) {
    if (status != expected) {
      throw new InvalidTransition("Invalid responsibility transition");
    }
  }

  /** A requested lifecycle operation is not valid for the current state. */
  public static final class InvalidTransition extends IllegalStateException {
    public InvalidTransition(String message) {
      super(message);
    }
  }

  private static void requireTitle(String value) {
    if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Title must not be blank");
    }
  }

  private static void requireDescription(String value) {
    if (value != null && value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Description contains an unsupported character");
    }
  }
}
