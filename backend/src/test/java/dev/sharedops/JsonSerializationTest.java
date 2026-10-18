package dev.sharedops;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@JsonTest
public class JsonSerializationTest {
  @Autowired JsonMapper jsonMapper;

  record SmokeDto(UUID id, String name, LocalDate date) {}

  @Test
  void shouldSerializeAndDeserializeDto() throws Exception {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 10, 18);
    SmokeDto original = new SmokeDto(id, "Test", date);
    // Serialization
    String json = jsonMapper.writeValueAsString(original);
    JsonNode root = jsonMapper.readTree(json);

    assertThat(root.get("id").asString()).isEqualTo(id.toString());
    assertThat(root.get("name").asString()).isEqualTo("Test");
    assertThat(root.get("date").asString()).isEqualTo(date.toString());
    // Deserialization
    SmokeDto restored = jsonMapper.readValue(json, SmokeDto.class);

    assertThat(restored).isEqualTo(original);
  }
}
