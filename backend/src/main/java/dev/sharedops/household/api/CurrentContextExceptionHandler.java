package dev.sharedops.household.api;

import dev.sharedops.common.api.ApiProblemFactory;
import dev.sharedops.household.application.UnauthenticatedHouseholdAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AccountController.class)
public class CurrentContextExceptionHandler {
  private final ApiProblemFactory problems;

  public CurrentContextExceptionHandler(ApiProblemFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(UnauthenticatedHouseholdAccessException.class)
  public ProblemDetail unauthenticated(UnauthenticatedHouseholdAccessException exception) {
    return problems.create(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
  }
}
