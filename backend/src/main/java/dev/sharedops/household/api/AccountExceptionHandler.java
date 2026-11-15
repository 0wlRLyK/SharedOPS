package dev.sharedops.household.api;

import dev.sharedops.common.api.ApiProblemFactory;
import dev.sharedops.household.domain.exception.EmailAlreadyRegisteredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AccountExceptionHandler {
  private final ApiProblemFactory problemFactory;

  public AccountExceptionHandler(ApiProblemFactory problemFactory) {
    this.problemFactory = problemFactory;
  }

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthenticationFailure(AuthenticationException exception) {
    return problemFactory.create(
        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
    return problemFactory.create(
        HttpStatus.CONFLICT,
        "EMAIL_ALREADY_REGISTERED",
        "An account with this email is already registered");
  }
}
