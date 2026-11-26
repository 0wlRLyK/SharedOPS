package dev.sharedops.task.api;

import dev.sharedops.common.api.ApiProblemFactory;
import dev.sharedops.household.contract.TaskInaccessible;
import dev.sharedops.household.contract.TaskUnauthenticated;
import dev.sharedops.task.application.TaskService;
import dev.sharedops.task.application.TaskVersionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TaskController.class)
public class TaskExceptionHandler {
  private final ApiProblemFactory problems;

  public TaskExceptionHandler(ApiProblemFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(TaskController.Invalid.class)
  ProblemDetail invalid(TaskController.Invalid e) {
    return problems.create(HttpStatus.BAD_REQUEST, e.code, "Invalid request");
  }

  @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class})
  ProblemDetail invalidArgument(RuntimeException e) {
    return problems.create(HttpStatus.BAD_REQUEST, "INVALID_VALUE", "Invalid request");
  }

  @ExceptionHandler(TaskService.InvalidAssignee.class)
  ProblemDetail assignee(TaskService.InvalidAssignee e) {
    return problems.create(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEE", "Invalid assignee");
  }

  @ExceptionHandler({TaskService.NotFound.class, TaskInaccessible.class})
  ProblemDetail notFound(RuntimeException e) {
    return problems.create(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found.");
  }

  @ExceptionHandler(TaskUnauthenticated.class)
  ProblemDetail unauthenticated(TaskUnauthenticated e) {
    return problems.create(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
  }

  @ExceptionHandler(TaskService.InvalidState.class)
  ProblemDetail state(TaskService.InvalidState e) {
    return problems.create(HttpStatus.CONFLICT, "INVALID_STATE", "Invalid state");
  }

  @ExceptionHandler(TaskVersionConflictException.class)
  ProblemDetail version(TaskVersionConflictException e) {
    return problems.create(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Version conflict");
  }
}
