package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.api.dto.CsrfTokenResponse;
import dev.sharedops.household.application.RegisterAccountService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PasswordRevocationIT.RaceConfig.class})
class PasswordRevocationIT {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired RegisterAccountService accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired BlockingEncoder encoder;

  @TestConfiguration
  static class RaceConfig {
    @Bean
    @Primary
    BlockingEncoder blockingEncoder() {
      return new BlockingEncoder();
    }
  }

  static class BlockingEncoder implements PasswordEncoder {
    final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder(12);
    volatile CountDownLatch matched;
    volatile CountDownLatch release;

    public String encode(CharSequence raw) {
      return delegate.encode(raw);
    }

    public boolean matches(CharSequence raw, String encoded) {
      boolean result = delegate.matches(raw, encoded);
      if (Thread.currentThread().getName().equals("stale-login") && matched != null) {
        matched.countDown();
        try {
          if (!release.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for password change");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }
      return result;
    }
  }

  record Client(Cookie cookie, CsrfTokenResponse csrf) {}

  Client csrf(Cookie cookie) throws Exception {
    var request = get("/api/v1/auth/csrf");
    if (cookie != null) {
      request.cookie(cookie);
    }
    var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
    Cookie issued = result.getResponse().getCookie("SESSION");
    return new Client(
        issued == null ? cookie : issued,
        json.readValue(result.getResponse().getContentAsString(), CsrfTokenResponse.class));
  }

  Client login(String email, String password) throws Exception {
    Client c = csrf(null);
    var result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(c.cookie())
                    .header(c.csrf().headerName(), c.csrf().token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            java.util.Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
    return csrf(result.getResponse().getCookie("SESSION"));
  }

  org.springframework.test.web.servlet.ResultActions change(
      Client c, String current, String replacement) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/password")
            .cookie(c.cookie())
            .header(c.csrf().headerName(), c.csrf().token())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    java.util.Map.of("currentPassword", current, "newPassword", replacement))));
  }

  @Test
  void longEmailCanLoginAndRevokeAllSessions() throws Exception {
    String email = "a".repeat(60) + "@" + "b".repeat(60) + ".example.com";
    assertThat(email.length()).isGreaterThan(100);
    accounts.register(email, "old pass");
    Client first = login(email, "old pass");
    Client second = login(email, "old pass");
    change(first, "old pass", "new pass").andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/protected").cookie(second.cookie()))
        .andExpect(status().isUnauthorized());
    login(email, "new pass");
  }

  @Test
  void wrongPasswordLeavesHashAndBothSessionsUsable() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    accounts.register(email, "old pass");
    Client first = login(email, "old pass");
    Client second = login(email, "old pass");
    String hash =
        jdbc.queryForObject(
            "select password_hash from accounts where email = ?", String.class, email);
    change(first, "wrong", "new pass")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    assertThat(
            jdbc.queryForObject(
                "select password_hash from accounts where email = ?", String.class, email))
        .isEqualTo(hash);
    mvc.perform(get("/api/v1/protected").cookie(first.cookie())).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/protected").cookie(second.cookie())).andExpect(status().isNotFound());
    login(email, "old pass");
  }

  @Test
  void successfulChangeRevokesOtherSessionsAndRejectsOldPassword() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    accounts.register(email, "old pass");
    Client first = login(email, "old pass");
    Client second = login(email, "old pass");
    change(first, "old pass", "new pass").andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/protected").cookie(second.cookie()))
        .andExpect(status().isUnauthorized());
    Client anonymous = csrf(null);
    mvc.perform(
            post("/api/v1/auth/login")
                .cookie(anonymous.cookie())
                .header(anonymous.csrf().headerName(), anonymous.csrf().token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        java.util.Map.of("email", email, "password", "old pass"))))
        .andExpect(status().isUnauthorized());
    login(email, "new pass");
  }

  @Test
  void loginCompletedAfterPasswordChangeCannotAuthorize() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    accounts.register(email, "old pass");
    Client first = login(email, "old pass");
    Client anonymous = csrf(null);
    encoder.matched = new CountDownLatch(1);
    encoder.release = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "stale-login"));
    try {
      var pending =
          executor.submit(
              () ->
                  mvc.perform(
                          post("/api/v1/auth/login")
                              .cookie(anonymous.cookie())
                              .header(anonymous.csrf().headerName(), anonymous.csrf().token())
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(
                                  json.writeValueAsString(
                                      java.util.Map.of("email", email, "password", "old pass"))))
                      .andExpect(status().isOk())
                      .andReturn());
      assertThat(encoder.matched.await(10, TimeUnit.SECONDS)).isTrue();
      change(first, "old pass", "new pass").andExpect(status().isNoContent());
      encoder.release.countDown();
      Cookie late = pending.get(10, TimeUnit.SECONDS).getResponse().getCookie("SESSION");
      assertThat(late).isNotNull();
      mvc.perform(get("/api/v1/protected").cookie(late)).andExpect(status().isUnauthorized());
    } finally {
      encoder.release.countDown();
      executor.shutdownNow();
      encoder.matched = null;
      encoder.release = null;
    }
  }

  @Test
  void staleSessionCredentialVersionCannotAuthorizeEvenIfSessionSurvives() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    accounts.register(email, "old pass");
    Client client = login(email, "old pass");
    jdbc.update(
        "update accounts set credential_version = credential_version + 1 where email = ?", email);
    mvc.perform(get("/api/v1/protected").cookie(client.cookie()))
        .andExpect(status().isUnauthorized());
  }
}
