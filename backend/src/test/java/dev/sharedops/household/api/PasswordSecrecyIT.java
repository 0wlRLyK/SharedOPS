package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.api.dto.CsrfTokenResponse;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class PasswordSecrecyIT {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;

  @Test
  void registrationAndAuthenticationStoreOnlyAdaptiveHashAndDoNotExposePasswords(
      CapturedOutput output) throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    String password = " secret-" + UUID.randomUUID() + " ";
    String wrong = "wrong-" + UUID.randomUUID();
    var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie cookie = csrf.getResponse().getCookie("SESSION");
    CsrfTokenResponse token =
        mapper.readValue(csrf.getResponse().getContentAsString(), CsrfTokenResponse.class);
    var registration =
        mvc.perform(
                post("/api/v1/auth/register")
                    .cookie(cookie)
                    .header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String hash =
        jdbc.queryForObject(
            "select password_hash from accounts where email = ?", String.class, email);
    assertThat(hash).matches("\\$2[aby]\\$12\\$.+").doesNotContain(password);
    assertThat(encoder.matches(password, hash)).isTrue();
    assertThat(encoder.matches(password.trim(), hash)).isFalse();
    var failed =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(cookie)
                    .header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + wrong + "\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn();
    var login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(cookie)
                    .header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(registration.getResponse().getContentAsString()).doesNotContain(password, hash);
    assertThat(failed.getResponse().getContentAsString()).doesNotContain(wrong, hash);
    assertThat(login.getResponse().getContentAsString()).doesNotContain(password, hash);
    assertThat(output.getAll()).doesNotContain(password, wrong, hash);
    Map<?, ?> contract;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      contract = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    Map<?, ?> components = (Map<?, ?>) contract.get("components");
    Map<?, ?> schemas = (Map<?, ?>) components.get("schemas");
    Map<?, ?> problem = (Map<?, ?>) schemas.get("ApiProblem");
    var actual = mapper.readTree(failed.getResponse().getContentAsString());
    for (Object field : (List<?>) problem.get("required")) {
      assertThat(actual.has((String) field)).isTrue();
    }
    Map<?, ?> properties = (Map<?, ?>) problem.get("properties");
    Map<?, ?> code = (Map<?, ?>) properties.get("code");
    assertThat(((List<?>) code.get("enum")).contains("INVALID_CREDENTIALS")).isTrue();
    assertThat(actual.path("code").asString()).isEqualTo("INVALID_CREDENTIALS");
  }
}
