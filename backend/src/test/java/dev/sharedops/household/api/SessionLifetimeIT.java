package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.security.SessionPolicy;
import dev.sharedops.household.api.dto.CsrfTokenResponse;
import dev.sharedops.household.application.RegisterAccountService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionLifetimeIT {
  @Autowired MockMvc mvc;
  @Autowired RegisterAccountService accounts;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate jdbc;

  private MvcResult login(boolean persistent) throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    accounts.register(email, "password");
    MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    CsrfTokenResponse token =
        json.readValue(csrf.getResponse().getContentAsString(), CsrfTokenResponse.class);
    return mvc.perform(
            post("/api/v1/auth/login")
                .cookie(csrf.getResponse().getCookie("SESSION"))
                .header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\""
                        + email
                        + "\",\"password\":\"password\",\"rememberMe\":"
                        + persistent
                        + "}"))
        .andExpect(status().isOk())
        .andReturn();
  }

  private String sessionId(Cookie cookie) {
    return new String(
        Base64.getDecoder().decode(cookie.getValue()), java.nio.charset.StandardCharsets.UTF_8);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void jdbcIdleExpiryAndCookie(boolean persistent) throws Exception {
    MvcResult login = login(persistent);
    Cookie cookie = login.getResponse().getCookie("SESSION");
    String header = login.getResponse().getHeader("Set-Cookie");
    assertThat(header).contains("Secure", "HttpOnly", "SameSite=Lax");
    if (persistent) {
      assertThat(header).contains("Max-Age=");
    } else {
      assertThat(header).doesNotContain("Max-Age=");
    }
    String id = sessionId(cookie);
    assertThat(
            jdbc.queryForObject(
                "SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE SESSION_ID=?",
                Integer.class,
                id))
        .isEqualTo(persistent ? 604800 : 1800);
    jdbc.update(
        "UPDATE SPRING_SESSION SET LAST_ACCESS_TIME=?, EXPIRY_TIME=? WHERE SESSION_ID=?",
        1L,
        1L,
        id);
    mvc.perform(get("/api/v1/auth/password").cookie(cookie)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void absoluteDeadlineRejectsActiveJdbcSession(boolean persistent) throws Exception {
    MvcResult login = login(persistent);
    Cookie cookie = login.getResponse().getCookie("SESSION");
    String id = sessionId(cookie);
    var bytes = new java.io.ByteArrayOutputStream();
    try (var output = new java.io.ObjectOutputStream(bytes)) {
      output.writeObject(Instant.now().minusSeconds(1).toEpochMilli());
    }
    assertThat(
            jdbc.update(
                "UPDATE SPRING_SESSION_ATTRIBUTES SET ATTRIBUTE_BYTES=? WHERE ATTRIBUTE_NAME=? AND SESSION_PRIMARY_ID=(SELECT PRIMARY_ID FROM SPRING_SESSION WHERE SESSION_ID=?)",
                bytes.toByteArray(),
                SessionPolicy.class.getName() + ".deadline",
                id))
        .isEqualTo(1);
    mvc.perform(get("/api/v1/auth/password").cookie(cookie)).andExpect(status().isUnauthorized());
  }
}
