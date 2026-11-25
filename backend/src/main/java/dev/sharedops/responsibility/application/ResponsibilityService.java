package dev.sharedops.responsibility.application;

import dev.sharedops.household.contract.HouseholdResponsibilityAccess;
import dev.sharedops.responsibility.domain.Responsibility;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for household-scoped responsibility operations. */
@Service
public class ResponsibilityService {
  private final HouseholdResponsibilityAccess households;
  private final ResponsibilityStore store;
  private final Clock clock;

  public ResponsibilityService(
      HouseholdResponsibilityAccess households, ResponsibilityStore store) {
    this(households, store, Clock.systemUTC());
  }

  @Autowired
  public ResponsibilityService(
      HouseholdResponsibilityAccess households, ResponsibilityStore store, Clock clock) {
    this.households = households;
    this.store = store;
    this.clock = Objects.requireNonNull(clock);
  }

  @Transactional
  public Responsibility create(
      UUID householdId, String title, String description, UUID ownerAccountId) {
    Objects.requireNonNull(householdId, "householdId");
    return households.withLockedHousehold(
        householdId,
        household -> {
          UUID owner =
              ownerAccountId == null
                  ? null
                  : household.activeMembershipId(ownerAccountId).orElse(null);
          if (owner == null) {
            throw new InvalidOwner();
          }
          Responsibility result =
              Responsibility.create(UUID.randomUUID(), householdId, title, description, owner);
          store.insert(result);
          return result;
        });
  }

  @Transactional
  public Responsibility get(UUID householdId, UUID responsibilityId) {
    Objects.requireNonNull(householdId, "householdId");
    Objects.requireNonNull(responsibilityId, "responsibilityId");
    return households.withLockedHousehold(
        householdId,
        household -> store.find(householdId, responsibilityId).orElseThrow(NotFound::new));
  }

  @Transactional
  public List<Responsibility> list(UUID householdId, boolean archived) {
    Objects.requireNonNull(householdId, "householdId");
    return households.withLockedHousehold(
        householdId, household -> store.list(householdId, archived));
  }

  /** Version-checked full replacement of details. */
  @Transactional
  public Responsibility edit(
      UUID householdId,
      UUID responsibilityId,
      long expectedVersion,
      String title,
      String description) {
    return households.withLockedHousehold(
        householdId,
        household -> {
          Responsibility original = lockedVersion(householdId, responsibilityId, expectedVersion);
          Responsibility changed = original.edit(title, description);
          return persistChanged(original, changed, expectedVersion);
        });
  }

  /** Transfers ownership without touching tasks; update and audit share the caller transaction. */
  @Transactional
  public Responsibility transfer(
      UUID householdId, UUID responsibilityId, long expectedVersion, UUID ownerAccountId) {
    return households.withLockedHousehold(
        householdId,
        household -> {
          Responsibility original = lockedVersion(householdId, responsibilityId, expectedVersion);
          if (original.status()
              == dev.sharedops.responsibility.domain.ResponsibilityStatus.ARCHIVED) {
            throw new Responsibility.InvalidTransition(
                "Archived responsibilities cannot be changed");
          }
          UUID owner =
              ownerAccountId == null
                  ? null
                  : household.activeMembershipId(ownerAccountId).orElse(null);
          if (owner == null) {
            throw new InvalidOwner();
          }
          Responsibility changed = original.transfer(owner);
          if (changed == original) {
            return original;
          }
          persistChanged(original, changed, expectedVersion);
          store.insertTransferFact(
              new ResponsibilityStore.TransferFact(
                  UUID.randomUUID(),
                  householdId,
                  responsibilityId,
                  household.actorAccountId(),
                  clock.instant(),
                  original.ownerMembershipId(),
                  owner));
          return changed;
        });
  }

  @Transactional
  public Responsibility pause(UUID householdId, UUID responsibilityId, long version) {
    return transition(householdId, responsibilityId, version, Responsibility::pause);
  }

  @Transactional
  public Responsibility resume(UUID householdId, UUID responsibilityId, long version) {
    return transition(householdId, responsibilityId, version, Responsibility::resume);
  }

  @Transactional
  public Responsibility archive(UUID householdId, UUID responsibilityId, long version) {
    return transition(householdId, responsibilityId, version, Responsibility::archive);
  }

  private Responsibility transition(
      UUID householdId,
      UUID responsibilityId,
      long version,
      java.util.function.UnaryOperator<Responsibility> operation) {
    return households.withLockedHousehold(
        householdId,
        household -> {
          Responsibility original = lockedVersion(householdId, responsibilityId, version);
          return persistChanged(original, operation.apply(original), version);
        });
  }

  /** A null owner means the optional replacement was omitted by the transport layer. */
  @Transactional
  public Responsibility restore(
      UUID householdId, UUID responsibilityId, long version, UUID ownerAccountId) {
    return households.withLockedHousehold(
        householdId,
        household -> {
          Responsibility original = lockedVersion(householdId, responsibilityId, version);
          if (original.status()
              != dev.sharedops.responsibility.domain.ResponsibilityStatus.ARCHIVED) {
            throw new Responsibility.InvalidTransition("Invalid responsibility transition");
          }
          UUID owner =
              ownerAccountId == null
                  ? (household.isActiveMembership(original.ownerMembershipId())
                      ? original.ownerMembershipId()
                      : null)
                  : household.activeMembershipId(ownerAccountId).orElse(null);
          if (owner == null) {
            throw new InvalidOwner();
          }
          return persistChanged(original, original.restore(owner), version);
        });
  }

  private Responsibility lockedVersion(
      UUID householdId, UUID responsibilityId, long expectedVersion) {
    if (expectedVersion < 0) {
      throw new IllegalArgumentException("Version must be nonnegative");
    }
    Responsibility original = store.lock(householdId, responsibilityId).orElseThrow(NotFound::new);
    if (original.version() != expectedVersion) {
      throw new VersionConflict();
    }
    return original;
  }

  private Responsibility persistChanged(
      Responsibility original, Responsibility changed, long expectedVersion) {
    if (changed != original && !store.update(changed, expectedVersion)) {
      throw new VersionConflict();
    }
    return changed;
  }

  public static final class VersionConflict extends RuntimeException {}

  public static final class InvalidOwner extends RuntimeException {}

  public static final class NotFound extends RuntimeException {}
}
