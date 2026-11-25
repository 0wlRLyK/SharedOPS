package dev.sharedops.responsibility.api;

import dev.sharedops.common.api.ApiProblemFactory;
import dev.sharedops.household.contract.ResponsibilityInaccessible;
import dev.sharedops.household.contract.ResponsibilityUnauthenticated;
import dev.sharedops.responsibility.application.ResponsibilityService;
import dev.sharedops.responsibility.domain.Responsibility;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ResponsibilityController.class)
public class ResponsibilityExceptionHandler {
  private final ApiProblemFactory problems;

  public ResponsibilityExceptionHandler(ApiProblemFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(ResponsibilityController.Invalid.class)
  ProblemDetail invalid(ResponsibilityController.Invalid e) {
    return problems.create(HttpStatus.BAD_REQUEST, e.code, "Invalid request");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalidArgument(IllegalArgumentException e) {
    return problems.create(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request");
  }

  @ExceptionHandler(ResponsibilityService.InvalidOwner.class)
  ProblemDetail invalidOwner(ResponsibilityService.InvalidOwner e) {
    return problems.create(HttpStatus.BAD_REQUEST, "INVALID_OWNER", "Invalid owner");
  }

  @ExceptionHandler({ResponsibilityService.NotFound.class, ResponsibilityInaccessible.class})
  ProblemDetail notFound(RuntimeException e) {
    return problems.create(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found.");
  }

  @ExceptionHandler(ResponsibilityUnauthenticated.class)
  ProblemDetail unauthenticated(ResponsibilityUnauthenticated e) {
    return problems.create(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
  }

  @ExceptionHandler(ResponsibilityService.VersionConflict.class)
  ProblemDetail version(ResponsibilityService.VersionConflict e) {
    return problems.create(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Version conflict");
  }

  @ExceptionHandler(Responsibility.InvalidTransition.class)
  ProblemDetail state(Responsibility.InvalidTransition e) {
    return problems.create(HttpStatus.CONFLICT, "INVALID_STATE", "Invalid transition");
  }
}
