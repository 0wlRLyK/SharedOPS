package dev.sharedops.responsibility.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class ResponsibilityOpenApiIT {
  @SuppressWarnings("unchecked")
  @Test
  void documentsStrictRequestsAndAllLifecycleRoutes() throws Exception {
    var yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    Map<String, Object> root = yaml.load(Files.readString(Path.of("../docs/api/openapi.yml")));
    Map<String, Object> paths = (Map<String, Object>) root.get("paths");
    String base = "/api/v1/households/{householdId}/responsibilities";
    for (String suffix :
        List.of(
            "",
            "/{responsibilityId}",
            "/{responsibilityId}/details",
            "/{responsibilityId}/transfer",
            "/{responsibilityId}/pause",
            "/{responsibilityId}/resume",
            "/{responsibilityId}/archive",
            "/{responsibilityId}/restore")) {
      assertThat(paths).containsKey(base + suffix);
    }
    Map<String, Object> components = (Map<String, Object>) root.get("components");
    Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
    for (String name :
        List.of(
            "ResponsibilityCreateRequest",
            "ResponsibilityDetailsRequest",
            "ResponsibilityTransferRequest",
            "ResponsibilityVersionRequest",
            "ResponsibilityRestoreRequest")) {
      Map<String, Object> schema = (Map<String, Object>) schemas.get(name);
      assertThat(schema.get("additionalProperties")).isEqualTo(false);
    }
    Map<String, Object> create = (Map<String, Object>) schemas.get("ResponsibilityCreateRequest");
    assertThat((List<String>) create.get("required"))
        .containsExactlyInAnyOrder("title", "mode", "ownerAccountId")
        .doesNotContain("version");
    Map<String, Object> restore = (Map<String, Object>) schemas.get("ResponsibilityRestoreRequest");
    assertThat((List<String>) restore.get("required")).containsExactly("version");
    Map<String, Object> props = (Map<String, Object>) restore.get("properties");
    assertThat(((Map<String, Object>) props.get("ownerAccountId")).get("type")).isEqualTo("string");
    Map<String, Object> version = (Map<String, Object>) schemas.get("ResponsibilityVersion");
    assertThat(((Number) version.get("minimum")).longValue()).isZero();
    assertThat(((Number) version.get("maximum")).longValue()).isEqualTo(Long.MAX_VALUE);
  }
}
