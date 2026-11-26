package dev.sharedops.household.contract;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.modulith.NamedInterface;

/** Authorized, transaction-scoped household access for task operations. */
@NamedInterface(value = "task", propagate = true)
public interface HouseholdTaskAccess {
  /**
   * Locks and authorizes the household in an existing transaction before invoking the callback. The
   * lock remains until transaction completion and the view expires when the callback returns.
   * Missing and inaccessible households are indistinguishable.
   *
   * @throws Unauthenticated when no authenticated account is present
   * @throws Inaccessible when the household is missing or the actor is not an active member
   */
  <T> T withLockedHousehold(UUID householdId, Function<LockedHousehold, T> action);

  interface LockedHousehold {
    UUID actorAccountId();

    String timezone();

    Optional<UUID> activeMembershipId(UUID accountId);

    boolean isActiveMembership(UUID membershipId);

    Optional<UUID> historicalAccountId(UUID membershipId);
  }

  final class Unauthenticated extends TaskUnauthenticated {}

  final class Inaccessible extends TaskInaccessible {}
}
