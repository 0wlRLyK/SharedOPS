package dev.sharedops.common.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.task.application.TaskVersionConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ApiExceptionHandlerTest.ValidationProbeController.class)
@Import({
  ApiExceptionHandler.class,
  ApiProblemFactory.class,
  ApiExceptionHandlerTest.ValidationProbeController.class
})
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

  @Autowired MockMvc mockMvc;

  @Test
  void shouldReturnStructuredValidationError() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":\"notEmail\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.detail").value("Invalid request"))
        .andExpect(jsonPath("$.fieldErrors.length()").value(2))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].code").value(hasItem("REQUIRED")))
        .andExpect(
            jsonPath("$.fieldErrors[?(@.field == 'email')].code").value(hasItem("INVALID_FORMAT")))
        .andExpect(
            jsonPath("$.fieldErrors[?(@.field == 'name')].message")
                .value(hasItem("Field is required")))
        .andExpect(
            jsonPath("$.fieldErrors[?(@.field == 'email')].message")
                .value(hasItem("Invalid field format")))
        .andExpect(jsonPath("$.fieldErrors[*].rejectedValue").doesNotExist())
        .andExpect(content().string(not(containsString("notEmail"))));
  }

  @Test
  void shouldReturnStructuredErrorForMalformedJson() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.detail").value("Malformed request body"))
        .andExpect(content().contentType("application/problem+json"))
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
  }

  @Test
  void shouldReturnInvalidValueForUnknownEnum() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Test\",\"email\":\"test@example.com\",\"status\":\"SECRET_UNKNOWN_VALUE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_VALUE"))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.detail").value("Invalid request value"))
        .andExpect(content().contentType("application/problem+json"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID_VALUE"))
        .andExpect(content().string(not(containsString("SECRET_UNKNOWN_VALUE"))))
        .andExpect(content().string(not(containsString("InvalidFormatException"))));
  }

  @Test
  void shouldIdentifyNestedArrayFieldWithoutEchoingInvalidValue() throws Exception {
    mockMvc
        .perform(
            post("/test/nested")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"status\":\"SECRET_UNKNOWN_VALUE\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("items[0].status"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID_VALUE"))
        .andExpect(content().string(not(containsString("SECRET_UNKNOWN_VALUE"))));
  }

  @Test
  void shouldNotExposeUserSuppliedMapKeysAsFieldPaths() throws Exception {
    mockMvc
        .perform(
            post("/test/map")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statuses\":{\"SECRET_KEY\":\"BAD\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_VALUE"))
        .andExpect(content().string(not(containsString("SECRET_KEY"))));
  }

  @Test
  void shouldReturnStructuredErrorForUnsupportedMediaType() throws Exception {
    mockMvc
        .perform(post("/test/validation").contentType(MediaType.TEXT_PLAIN).content("{}"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
        .andExpect(jsonPath("$.title").value("Unsupported Media Type"))
        .andExpect(jsonPath("$.detail").value("Unsupported request content type"))
        .andExpect(content().contentType("application/problem+json"))
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
  }

  @Test
  void shouldReturnInvalidValueForIncompatibleJsonType() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":{\"secret\":\"hidden-value\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                assertInstanceOf(
                    tools.jackson.databind.exc.MismatchedInputException.class,
                    result.getResolvedException().getCause()))
        .andExpect(jsonPath("$.code").value("INVALID_VALUE"))
        .andExpect(jsonPath("$.detail").value("Invalid request value"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
        .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID_VALUE"))
        .andExpect(content().string(not(containsString("hidden-value"))))
        .andExpect(content().string(not(containsString("MismatchedInputException"))));
  }

  @Test
  void shouldReturnRequiredForMissingName() throws Exception {
    mockMvc
        .perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].code").value(hasItem("REQUIRED")))
        .andExpect(
            jsonPath("$.fieldErrors[?(@.field == 'name')].message")
                .value(hasItem("Field is required")));
  }

  @Test
  void shouldReturnRequiredForNullName() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].code").value(hasItem("REQUIRED")))
        .andExpect(
            jsonPath("$.fieldErrors[?(@.field == 'name')].message")
                .value(hasItem("Field is required")));
  }

  @Test
  void shouldReturnMalformedJsonForEmptyBody() throws Exception {
    mockMvc
        .perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
        .andExpect(jsonPath("$.detail").value("Malformed request body"))
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
  }

  @Test
  void shouldNormalizeEmail() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"email\":\"     Test@Example.COM         \"}"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
        .andExpect(content().string("test@example.com"));
  }

  @Test
  void shouldRejectBlankEmailAfterNormalization() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"email\":\"              \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].code").value(hasItem("REQUIRED")))
        .andExpect(
            jsonPath("$.fieldErrors[?(@.field == 'email')].message")
                .value(hasItem("Field is required")));
  }

  @Test
  void shouldReturnVersionConflict() throws Exception {
    mockMvc
        .perform(get("/test/version-conflict"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
        .andExpect(jsonPath("$.detail").value("Resource version conflict"))
        .andExpect(content().string(not(containsString("Internal diagnostic message"))));
  }

  record ValidationProbeRequest(
      @NotBlank String name, @NotBlank @Email String email, ProbeStatus status) {
    public ValidationProbeRequest {
      if (email != null) {
        email = email.strip().toLowerCase(Locale.ROOT);
      }
    }
  }

  enum ProbeStatus {
    OPEN,
    COMPLETED
  }

  record NestedRequest(List<NestedItem> items) {}

  record NestedItem(ProbeStatus status) {}

  record MapRequest(Map<String, ProbeStatus> statuses) {}

  @RestController
  static class ValidationProbeController {

    @PostMapping("/test/map")
    void map(@RequestBody MapRequest request) {}

    @PostMapping("/test/nested")
    void nested(@RequestBody NestedRequest request) {}

    @PostMapping("/test/validation")
    String validate(@Valid @RequestBody ValidationProbeRequest request) {
      return request.email();
    }

    @GetMapping("/test/version-conflict")
    void versionConflict() {
      throw new TaskVersionConflictException("Internal diagnostic message");
    }
  }
}
