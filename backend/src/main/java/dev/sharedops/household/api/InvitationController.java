package dev.sharedops.household.api;

import dev.sharedops.household.application.HouseholdSnapshot;
import dev.sharedops.household.application.InvalidHouseholdException;
import dev.sharedops.household.application.InvitationService;
import dev.sharedops.household.application.IssuedInvitation;
import dev.sharedops.household.domain.InvitationToken;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvitationController {
  private final InvitationService invitations;

  public InvitationController(InvitationService invitations) {
    this.invitations = invitations;
  }

  @PostMapping("/api/v1/households/{householdId}/invitations")
  public ResponseEntity<InvitationResponse> create(
      @PathVariable("householdId") String householdId) {
    IssuedInvitation issued = invitations.create(parseId(householdId));
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(new InvitationResponse(issued.id(), issued.token(), issued.expiresAt()));
  }

  @DeleteMapping("/api/v1/households/{householdId}/invitations/{invitationId}")
  public ResponseEntity<Void> revoke(
      @PathVariable("householdId") String householdId,
      @PathVariable("invitationId") String invitationId) {
    invitations.revoke(parseId(householdId), parseId(invitationId));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/v1/invitations/accept")
  public ResponseEntity<HouseholdSnapshot> accept(@RequestBody AcceptRequest request) {
    try {
      InvitationToken.parse(request == null ? null : request.token());
    } catch (IllegalArgumentException invalid) {
      throw new InvalidHouseholdException();
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(invitations.accept(request.token()));
  }

  private static UUID parseId(String raw) {
    try {
      UUID id = UUID.fromString(raw);
      if (!id.toString().equalsIgnoreCase(raw)) {
        throw new IllegalArgumentException();
      }
      return id;
    } catch (IllegalArgumentException invalid) {
      throw new InvalidHouseholdException();
    }
  }

  public record AcceptRequest(String token) {
    @Override
    public String toString() {
      return "AcceptRequest[token=REDACTED]";
    }
  }

  public record InvitationResponse(UUID id, String token, Instant expiresAt) {
    @Override
    public String toString() {
      return "InvitationResponse[id=" + id + ", token=REDACTED, expiresAt=" + expiresAt + "]";
    }
  }
}
