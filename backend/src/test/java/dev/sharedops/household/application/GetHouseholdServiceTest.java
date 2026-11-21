package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetHouseholdServiceTest {
  private final CurrentAccount identity = mock(CurrentAccount.class);
  private final MembershipStore memberships = mock(MembershipStore.class);
  private final HouseholdStore households = mock(HouseholdStore.class);
  private final GetHouseholdService service =
      new GetHouseholdService(identity, memberships, households);

  @Test
  void deniesMissingIdentityBeforeAnyPersistence() {
    when(identity.authenticatedAccountId()).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(UUID.randomUUID()))
        .isInstanceOf(UnauthenticatedHouseholdAccessException.class);
    verifyNoInteractions(memberships, households);
  }

  @Test
  void foreignIdentifierIsDeniedBeforeHouseholdRead() {
    var actor = UUID.randomUUID();
    var own = UUID.randomUUID();
    var foreign = UUID.randomUUID();
    when(identity.authenticatedAccountId()).thenReturn(Optional.of(actor));
    when(memberships.findActiveHouseholdId(actor)).thenReturn(Optional.of(own));
    assertThatThrownBy(() -> service.get(foreign)).isInstanceOf(HouseholdNotFoundException.class);
    verifyNoInteractions(households);
  }

  @Test
  void authorizedReadReturnsActualHousehold() {
    var actor = UUID.randomUUID();
    var home = UUID.randomUUID();
    var expected = new HouseholdSnapshot(home, "Home", null, "EUR", "Europe/Berlin");
    when(identity.authenticatedAccountId()).thenReturn(Optional.of(actor));
    when(memberships.findActiveHouseholdId(actor)).thenReturn(Optional.of(home));
    when(households.findById(home)).thenReturn(Optional.of(expected));
    assertThat(service.get(home)).isEqualTo(expected);
  }
}
