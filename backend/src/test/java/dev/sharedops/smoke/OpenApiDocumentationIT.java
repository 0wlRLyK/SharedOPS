package dev.sharedops.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.common.api.ApiExceptionHandler;
import dev.sharedops.common.api.ApiProblemFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = OpenApiDocumentationIT.ContractProbeController.class)
@Import({
  ApiExceptionHandler.class,
  ApiProblemFactory.class,
  OpenApiDocumentationIT.ContractProbeController.class
})
@AutoConfigureMockMvc(addFilters = false)
class OpenApiDocumentationIT {
  @Autowired MockMvc mockMvc;
  @Autowired JsonMapper jsonMapper;

  @Test
  void manualContractDescribesActualValidationResponse() throws Exception {
    Map<String, Object> contract;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      contract = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    assertThat(contract.get("openapi")).isEqualTo("3.1.0");
    Map<?, ?> components = (Map<?, ?>) contract.get("components");
    Map<?, ?> schemas = (Map<?, ?>) components.get("schemas");
    Map<?, ?> problem = (Map<?, ?>) schemas.get("ApiProblem");
    Map<?, ?> properties = (Map<?, ?>) problem.get("properties");
    Map<?, ?> code = (Map<?, ?>) properties.get("code");
    List<String> codes = ((List<?>) code.get("enum")).stream().map(String.class::cast).toList();
    assertThat(codes)
        .contains(
            "VALIDATION_ERROR",
            "INVALID_VALUE",
            "MALFORMED_JSON",
            "UNSUPPORTED_MEDIA_TYPE",
            "UNAUTHORIZED",
            "FORBIDDEN",
            "NOT_FOUND",
            "VERSION_CONFLICT",
            "RATE_LIMITED");
    Map<?, ?> responses = (Map<?, ?>) components.get("responses");
    assertThat(responses.containsKey("RateLimited")).isTrue();

    String body =
        mockMvc
            .perform(post("/test/contract").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode actual = jsonMapper.readTree(body);
    for (Object required : (List<?>) problem.get("required")) {
      assertThat(actual.has((String) required)).as("required property %s", required).isTrue();
    }
    assertThat(codes).contains(actual.path("code").asString());
    assertThat(actual.path("fieldErrors").get(0).path("field").asString()).isEqualTo("name");
  }

  record Request(@NotBlank String name) {}

  @RestController
  static class ContractProbeController {
    @PostMapping("/test/contract")
    void validate(@Valid @RequestBody Request request) {}
  }
}
