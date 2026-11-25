package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentContextServiceTest {
  private final CurrentAccount currentAccount = mock(CurrentAccount.class);
  private final CurrentContextStore store = mock(CurrentContextStore.class);
  private final CurrentContextService service = new CurrentContextService(currentAccount, store);

  @Test
  void returnsCurrentProjectionForAuthenticatedActor() {
    var actor = UUID.randomUUID();
    var household = new HouseholdSnapshot(UUID.randomUUID(), "Home", null, "EUR", "Europe/Berlin");
    var expected = new CurrentContext(actor, "current@example.test", household);
    when(currentAccount.authenticatedAccountId()).thenReturn(Optional.of(actor));
    when(store.findForAccount(actor)).thenReturn(Optional.of(expected));

    assertThat(service.get()).isEqualTo(expected);
    verify(store).findForAccount(actor);
    verifyNoMoreInteractions(store);
  }

  @Test
  void returnsAccountWithNullHouseholdWhenStoreHasNoActiveMembership() {
    var actor = UUID.randomUUID();
    var expected = new CurrentContext(actor, "current@example.test", null);
    when(currentAccount.authenticatedAccountId()).thenReturn(Optional.of(actor));
    when(store.findForAccount(actor)).thenReturn(Optional.of(expected));

    assertThat(service.get()).isEqualTo(expected);
    assertThat(service.get().household()).isNull();
  }

  @Test
  void missingIdentityIsRejectedBeforeStoreAccess() {
    when(currentAccount.authenticatedAccountId()).thenReturn(Optional.empty());

    assertThatThrownBy(service::get).isInstanceOf(UnauthenticatedHouseholdAccessException.class);
    verifyNoInteractions(store);
  }

  @Test
  void missingAccountIsRejectedWithoutReturningContext() {
    var actor = UUID.randomUUID();
    when(currentAccount.authenticatedAccountId()).thenReturn(Optional.of(actor));
    when(store.findForAccount(actor)).thenReturn(Optional.empty());

    assertThatThrownBy(service::get).isInstanceOf(UnauthenticatedHouseholdAccessException.class);
    verify(store).findForAccount(actor);
    verifyNoMoreInteractions(store);
  }
}
