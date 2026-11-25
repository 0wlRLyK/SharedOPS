package dev.sharedops.household.contract;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.modulith.NamedInterface;

/** Household-scoped authorization for responsibility mutations. */
@NamedInterface(value = "responsibility", propagate = true)
public interface HouseholdResponsibilityAccess {
  /**
   * Executes in an existing transaction after locking the household and authorizing its actor. The
   * lock remains held until transaction completion. The supplied view is valid only during the
   * callback. An absent or inaccessible household produces the same exception.
   *
   * @throws Unauthenticated if there is no authenticated actor
   * @throws Inaccessible if the household is absent or the actor is not an active member
   */
  <T> T withLockedHousehold(UUID householdId, Function<LockedHousehold, T> action);

  interface LockedHousehold {
    UUID actorAccountId();

    /** Returns the active same-household membership ID, without disclosing other memberships. */
    Optional<UUID> activeMembershipId(UUID accountId);

    /** Resolves a same-household membership, including inactive historical memberships. */
    Optional<UUID> historicalAccountId(UUID membershipId);

    /** Checks whether a same-household membership remains active. */
    boolean isActiveMembership(UUID membershipId);
  }

  final class Unauthenticated extends ResponsibilityUnauthenticated {}

  final class Inaccessible extends ResponsibilityInaccessible {}
}
