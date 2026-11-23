package dev.sharedops.household.api;

import dev.sharedops.common.api.ApiProblemFactory;
import dev.sharedops.household.application.HouseholdNotFoundException;
import dev.sharedops.household.application.InvalidHouseholdException;
import dev.sharedops.household.application.InvalidInvitationException;
import dev.sharedops.household.application.InvitationConflictException;
import dev.sharedops.household.application.UnauthenticatedHouseholdAccessException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InvitationController.class)
public class InvitationExceptionHandler {
  private final ApiProblemFactory problems;

  public InvitationExceptionHandler(ApiProblemFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler({HouseholdNotFoundException.class, InvalidInvitationException.class})
  public ResponseEntity<Map<String, Object>> notFound(RuntimeException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(
            Map.of(
                "title",
                "Not Found",
                "status",
                404,
                "detail",
                "Resource not found.",
                "code",
                "NOT_FOUND"));
  }

  @ExceptionHandler(InvalidHouseholdException.class)
  public ProblemDetail invalid(InvalidHouseholdException exception) {
    return problems.create(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request");
  }

  @ExceptionHandler(InvitationConflictException.class)
  public ProblemDetail conflict(InvitationConflictException exception) {
    return problems.create(HttpStatus.CONFLICT, "INVITATION_CONFLICT", "Invitation cannot be used");
  }

  @ExceptionHandler(UnauthenticatedHouseholdAccessException.class)
  public ProblemDetail unauthenticated(UnauthenticatedHouseholdAccessException exception) {
    return problems.create(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
  }
}
