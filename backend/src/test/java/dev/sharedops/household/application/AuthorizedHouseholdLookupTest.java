package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.household.domain.HouseholdId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizedHouseholdLookupTest {
  @Test
  void noActorOrNullIdNeverReadsMemberships() {
    var calls = new int[1];
    MembershipStore memberships =
        new MembershipStore() {
          public Optional<UUID> findActiveHouseholdId(UUID id) {
            throw new AssertionError("unexpected lookup");
          }

          public boolean isActiveMember(UUID a, UUID h) {
            calls[0]++;
            return true;
          }

          public void insertActive(UUID a, UUID h) {
            throw new AssertionError("unexpected write");
          }
        };
    var noActor = new AuthorizedHouseholdLookup(Optional::<UUID>empty, memberships);
    assertThat(noActor.isAccessible(new HouseholdId(UUID.randomUUID()))).isFalse();
    var authenticated =
        new AuthorizedHouseholdLookup(() -> Optional.of(UUID.randomUUID()), memberships);
    assertThat(authenticated.isAccessible(null)).isFalse();
    assertThat(calls[0]).isZero();
  }
}
