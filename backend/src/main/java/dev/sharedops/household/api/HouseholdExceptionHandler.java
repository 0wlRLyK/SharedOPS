package dev.sharedops.household.api;

import dev.sharedops.common.api.ApiProblemFactory;
import dev.sharedops.household.application.ActiveHouseholdExistsException;
import dev.sharedops.household.application.HouseholdNotFoundException;
import dev.sharedops.household.application.InvalidHouseholdException;
import dev.sharedops.household.application.UnauthenticatedHouseholdAccessException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = HouseholdController.class)
public class HouseholdExceptionHandler {
  private final ApiProblemFactory problems;

  public HouseholdExceptionHandler(ApiProblemFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(HouseholdNotFoundException.class)
  public ResponseEntity<Map<String, Object>> notFound(HouseholdNotFoundException exception) {
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
  public ResponseEntity<Map<String, Object>> invalid(InvalidHouseholdException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(
            Map.of(
                "title",
                "Bad Request",
                "status",
                400,
                "detail",
                "Invalid request",
                "code",
                "VALIDATION_ERROR"));
  }

  @ExceptionHandler(ActiveHouseholdExistsException.class)
  public ProblemDetail conflict(ActiveHouseholdExistsException exception) {
    return problems.create(
        HttpStatus.CONFLICT, "ACTIVE_HOUSEHOLD_EXISTS", "Account already has an active household");
  }

  @ExceptionHandler(UnauthenticatedHouseholdAccessException.class)
  public ProblemDetail unauthenticated(UnauthenticatedHouseholdAccessException exception) {
    return problems.create(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
  }
}
