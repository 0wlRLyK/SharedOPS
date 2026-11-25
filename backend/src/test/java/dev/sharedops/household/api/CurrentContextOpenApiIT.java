package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CurrentContextOpenApiIT {
  @Autowired MockMvc mvc;

  private static Map<?, ?> map(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<?, ?>) value;
  }

  @Test
  void contractDeclaresRequiredResponseFieldsAndActualUnauthorizedResponse() throws Exception {
    Map<?, ?> api;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      api = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    var paths = map(api.get("paths"));
    var operation = map(map(paths.get("/api/v1/auth/me")).get("get"));
    var responses = map(operation.get("responses"));
    var ok = map(responses.get("200"));
    var unauthorized = map(responses.get("401"));
    for (var response : List.of(ok, unauthorized)) {
      var cache = map(map(map(response.get("headers")).get("Cache-Control")).get("schema"));
      assertThat(cache.get("const")).isEqualTo("no-store");
    }
    var successContent = map(map(ok.get("content")).get("application/json"));
    assertThat(map(successContent.get("schema")).get("$ref"))
        .isEqualTo("#/components/schemas/CurrentContextResponse");
    var errorContent = map(map(unauthorized.get("content")).get("application/problem+json"));
    assertThat(map(errorContent.get("schema")).get("$ref"))
        .isEqualTo("#/components/schemas/ApiProblem");
    var schemas = map(map(api.get("components")).get("schemas"));
    var context = map(schemas.get("CurrentContextResponse"));
    assertThat(new HashSet<>((List<?>) context.get("required")))
        .isEqualTo(Set.of("account", "household"));
    var properties = map(context.get("properties"));
    assertThat(map(properties.get("account")).get("$ref"))
        .isEqualTo("#/components/schemas/AccountResponse");
    var variants = (List<?>) map(properties.get("household")).get("oneOf");
    assertThat(variants).hasSize(2);
    assertThat(map(variants.get(0)).get("$ref"))
        .isEqualTo("#/components/schemas/CurrentContextHousehold");
    assertThat(map(variants.get(1)).get("type")).isEqualTo("null");
    var household = map(schemas.get("CurrentContextHousehold"));
    assertThat(new HashSet<>((List<?>) household.get("required")))
        .isEqualTo(Set.of("id", "name", "icon", "currency", "timezone"));
    var fields = map(household.get("properties"));
    assertThat(map(fields.get("id")).get("format")).isEqualTo("uuid");
    assertThat(map(fields.get("icon")).get("type")).isEqualTo(List.of("string", "null"));
    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Cache-Control", "no-store"));
  }
}
