package dev.sharedops.config.security;

import dev.sharedops.common.api.ApiProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final ApiProblemFactory problemFactory;
  private final JsonMapper jsonMapper;

  public ApiAuthenticationEntryPoint(ApiProblemFactory problemFactory, JsonMapper jsonMapper) {
    this.problemFactory = problemFactory;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    ProblemDetail problem =
        problemFactory.create(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), problem);
  }
}
