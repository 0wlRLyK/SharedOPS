package dev.sharedops;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.smoke.openapi.OpenApiSmokeController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import({TestcontainersConfiguration.class, OpenApiSmokeController.class})
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.locations=classpath:db/persistence-smoke",
      "spring.security.user.name=openapi-smoke",
      "spring.security.user.password=openapi-smoke-password"
    })
class OpenApiDocumentationIT {

  @LocalServerPort int port;

  @Autowired JsonMapper jsonMapper;

  @Test
  void exposesOpenApiDocumentation() throws Exception {
    String credentials =
        Base64.getEncoder()
            .encodeToString(
                "openapi-smoke:openapi-smoke-password".getBytes(StandardCharsets.UTF_8));
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/v3/api-docs"))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("Authorization", "Basic " + credentials)
            .GET()
            .build();

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);

      assertThat(response.headers().firstValue("content-type").orElse(""))
          .contains("application/json");

      JsonNode root = jsonMapper.readTree(response.body());

      String endpointPath = "/api/_smoke/openapi";
      String schemaName = "OpenApiSmokeResponse";

      assertThat(root.path("openapi").asString()).startsWith("3.");

      JsonNode operation = root.path("paths").path(endpointPath).path("get");

      assertThat(operation.isObject()).as("documented GET smoke operation").isTrue();
      JsonNode responseSchema =
          operation.path("responses").path("200").path("content").path("*/*").path("schema");
      assertThat(responseSchema.path("$ref").asString())
          .isEqualTo("#/components/schemas/OpenApiSmokeResponse");

      JsonNode schema = root.path("components").path("schemas").path(schemaName);
      assertThat(schema.path("type").asString()).isEqualTo("object");
      assertThat(schema.path("properties").path("message").path("type").asString())
          .isEqualTo("string");
      assertThat(schema.path("properties").path("version").path("type").asString())
          .isEqualTo("integer");
    }
  }
}
