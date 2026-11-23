package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.sharedops.common.api.ApiProblemFactory;
import dev.sharedops.household.application.HouseholdNotFoundException;
import dev.sharedops.household.application.HouseholdSnapshot;
import dev.sharedops.household.application.InvalidHouseholdException;
import dev.sharedops.household.application.InvalidInvitationException;
import dev.sharedops.household.application.InvitationConflictException;
import dev.sharedops.household.application.InvitationService;
import dev.sharedops.household.application.IssuedInvitation;
import dev.sharedops.household.domain.InvitationToken;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InvitationControllerTest {
  private final InvitationService service = mock(InvitationService.class);
  private final InvitationController controller = new InvitationController(service);
  private final InvitationExceptionHandler errors =
      new InvitationExceptionHandler(new ApiProblemFactory());
  private final UUID home = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();

  @Test
  void creationReturnsOnlyBodySecretAndNoStore() {
    String token = InvitationToken.generate().reveal();
    Instant expiry = Instant.parse("2026-11-23T12:00:00Z");
    when(service.create(home)).thenReturn(new IssuedInvitation(id, token, expiry));
    var result = controller.create(home.toString());
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(result.getHeaders().getLocation()).isNull();
    assertThat(result.getBody().id()).isEqualTo(id);
    assertThat(result.getBody().token()).isEqualTo(token);
    assertThat(result.getBody().expiresAt()).isEqualTo(expiry);
    assertThat(result.getBody().toString()).doesNotContain(token);
    assertThat(result.getHeaders().toString()).doesNotContain(token);
  }

  @Test
  void validAcceptancePassesOnlyBodyTokenAndReturnsNoStore() {
    String token = InvitationToken.generate().reveal();
    var household = new HouseholdSnapshot(home, "Home", null, "EUR", "Europe/Berlin");
    when(service.accept(token)).thenReturn(household);
    var request = new InvitationController.AcceptRequest(token);
    var result = controller.accept(request);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isEqualTo(household);
    assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(request.toString()).doesNotContain(token);
    verify(service).accept(token);
  }

  @Test
  void invalidTokenAndIdentifiersAreRejectedBeforeServiceWithoutEcho() {
    String invalid = "synthetic-secret-invalid";
    assertThatThrownBy(() -> controller.accept(new InvitationController.AcceptRequest(invalid)))
        .isInstanceOf(InvalidHouseholdException.class)
        .hasMessage(null);
    assertThatThrownBy(() -> controller.create("not-a-uuid"))
        .isInstanceOf(InvalidHouseholdException.class);
    assertThatThrownBy(() -> controller.revoke(home.toString(), "not-a-uuid"))
        .isInstanceOf(InvalidHouseholdException.class);
    verifyNoInteractions(service);
  }

  @Test
  void revokeDelegatesBothIdentifiersAndReturnsNoContent() {
    assertThat(controller.revoke(home.toString(), id.toString()).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    verify(service).revoke(home, id);
    verify(service, never()).create(home);
  }

  @Test
  void unknownInvitationAndInaccessibleHouseholdUseIdenticalNotFoundBodies() {
    var unknown = errors.notFound(new InvalidInvitationException());
    var foreign = errors.notFound(new HouseholdNotFoundException());
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(unknown.getBody()).isEqualTo(foreign.getBody());
    assertThat(errors.conflict(new InvitationConflictException()).getStatus()).isEqualTo(409);
    assertThat(errors.invalid(new InvalidHouseholdException()).getStatus()).isEqualTo(400);
  }
}
