package dev.sharedops.common.api;

import dev.sharedops.task.application.TaskVersionConflictException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.MismatchedInputException;

@RestControllerAdvice
public class ApiExceptionHandler {

  private final ApiProblemFactory problemFactory;

  public ApiExceptionHandler(ApiProblemFactory problemFactory) {
    this.problemFactory = problemFactory;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleNotValidArgument(MethodArgumentNotValidException exception) {

    ProblemDetail problem =
        problemFactory.create(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request");
    problem.setProperty(
        "fieldErrors",
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                fieldError -> {
                  String code = mapValidationCode(fieldError.getCode());
                  return new FieldErrorDetail(
                      fieldError.getField(), code, mapValidationMessage(code));
                })
            .toList());

    return problem;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
    if (exception.getCause() instanceof MismatchedInputException mismatch) {
      ProblemDetail problem =
          problemFactory.create(HttpStatus.BAD_REQUEST, "INVALID_VALUE", "Invalid request value");
      StringBuilder path = new StringBuilder();
      for (var reference : mismatch.getPath()) {
        if (reference.from() instanceof Map<?, ?>) {
          break;
        }
        if (reference.getPropertyName() != null) {
          if (!path.isEmpty()) {
            path.append('.');
          }
          path.append(reference.getPropertyName());
        } else if (reference.getIndex() >= 0) {
          path.append('[').append(reference.getIndex()).append(']');
        }
      }
      if (!path.isEmpty()) {
        problem.setProperty(
            "fieldErrors",
            List.of(new FieldErrorDetail(path.toString(), "INVALID_VALUE", "Invalid field value")));
      }
      return problem;
    }
    return problemFactory.create(
        HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Malformed request body");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
    return problemFactory.create(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "Unsupported request content type");
  }

  @ExceptionHandler(TaskVersionConflictException.class)
  public ProblemDetail handleTaskVersionConflict(TaskVersionConflictException exception) {
    return problemFactory.create(
        HttpStatus.CONFLICT, "VERSION_CONFLICT", "Resource version conflict");
  }

  private String mapValidationCode(String validationCode) {
    return switch (validationCode) {
      case "NotBlank", "NotNull" -> "REQUIRED";
      case "Email" -> "INVALID_FORMAT";
      case null, default -> "INVALID_VALUE";
    };
  }

  private String mapValidationMessage(String apiCode) {
    return switch (apiCode) {
      case "REQUIRED" -> "Field is required";
      case "INVALID_FORMAT" -> "Invalid field format";
      case null, default -> "Invalid field value";
    };
  }
}
