package dev.sharedops.config.security;

import dev.sharedops.common.api.ApiProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
  private final ApiProblemFactory problemFactory;
  private final JsonMapper jsonMapper;

  public ApiAccessDeniedHandler(ApiProblemFactory problemFactory, JsonMapper jsonMapper) {
    this.problemFactory = problemFactory;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    ProblemDetail problem =
        problemFactory.create(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), problem);
  }
}
