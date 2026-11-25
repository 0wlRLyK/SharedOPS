package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.security.SessionPolicy;
import dev.sharedops.household.application.CurrentContextStore;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CurrentContextHttpIT {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionRepository<? extends Session> sessions;
  @MockitoSpyBean CurrentContextStore contextStore;
  private final UUID a = UUID.randomUUID();
  private final UUID b = UUID.randomUUID();
  private final UUID inactive = UUID.randomUUID();
  private final UUID absent = UUID.randomUUID();
  private final UUID homeA = UUID.randomUUID();
  private final UUID homeB = UUID.randomUUID();

  @BeforeEach
  void setup() {
    for (UUID id : List.of(a, b, inactive, absent)) {
      jdbc.update(
          "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
          id,
          id + "@example.test");
    }
    jdbc.update(
        "INSERT INTO households (id,name,icon,currency,timezone) VALUES (?, 'Home A', NULL, 'EUR', 'Europe/Berlin'), (?, 'Home B', 'star', 'USD', 'America/New_York')",
        homeA,
        homeB);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?, ?, ?, true, 1), (?, ?, ?, true, 1), (?, ?, ?, false, NULL), (?, ?, ?, false, NULL)",
        UUID.randomUUID(),
        a,
        homeA,
        UUID.randomUUID(),
        b,
        homeB,
        UUID.randomUUID(),
        a,
        homeB,
        UUID.randomUUID(),
        inactive,
        homeB);
  }

  @AfterEach
  void cleanup() {
    jdbc.update(
        "DELETE FROM household_memberships WHERE account_id IN (?, ?, ?, ?)",
        a,
        b,
        inactive,
        absent);
    jdbc.update("DELETE FROM households WHERE id IN (?, ?)", homeA, homeB);
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?, ?, ?)", a, b, inactive, absent);
  }

  private <S extends Session> Cookie cookie(SessionRepository<S> repository, UUID id) {
    var principal = new AccountPrincipal(id, id + "@example.test", "hash", 0);
    var auth =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
    MockHttpSession policy = new MockHttpSession();
    SessionPolicy.initialize(policy, false, Instant.now());
    S session = repository.createSession();
    var names = policy.getAttributeNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      session.setAttribute(name, policy.getAttribute(name));
    }
    session.setMaxInactiveInterval(Duration.ofSeconds(policy.getMaxInactiveInterval()));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    repository.save(session);
    return new Cookie(
        "SESSION",
        Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void authenticatedSessionsSeeOnlyCurrentStoredAccountAndActiveHousehold() throws Exception {
    Cookie actor = cookie(sessions, a);
    jdbc.update("UPDATE accounts SET email = ? WHERE id = ?", "updated-" + a + "@example.test", a);
    mvc.perform(get("/api/v1/auth/me").cookie(actor))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.account.id").value(a.toString()))
        .andExpect(jsonPath("$.account.email").value("updated-" + a + "@example.test"))
        .andExpect(jsonPath("$.household.id").value(homeA.toString()))
        .andExpect(jsonPath("$.household.name").value("Home A"))
        .andExpect(jsonPath("$.household.icon").value(nullValue()))
        .andExpect(jsonPath("$.household.currency").value("EUR"))
        .andExpect(jsonPath("$.household.timezone").value("Europe/Berlin"));
    mvc.perform(get("/api/v1/auth/me").cookie(cookie(sessions, b)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.id").value(b.toString()))
        .andExpect(jsonPath("$.household.id").value(homeB.toString()))
        .andExpect(jsonPath("$.household.icon").value("star"));
    for (UUID id : List.of(inactive, absent)) {
      mvc.perform(get("/api/v1/auth/me").cookie(cookie(sessions, id)))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(jsonPath("$.account.id").value(id.toString()))
          .andExpect(jsonPath("$.household").value(nullValue()));
    }
  }

  @Test
  void anonymousRequestHasProblemAndNoPrivateDataAndNoStore() throws Exception {
    var response =
        mvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.account").doesNotExist())
            .andExpect(jsonPath("$.household").doesNotExist())
            .andReturn()
            .getResponse();
    assertThat(response.getContentAsString()).doesNotContain(a.toString(), homeA.toString());
  }

  @Test
  void accountMissingAtContextReadReturnsUnauthorizedWithoutDisclosure() throws Exception {
    Cookie actor = cookie(sessions, a);
    // Simulate deletion after the credential-version filter has authenticated the session.
    doReturn(Optional.empty()).when(contextStore).findForAccount(a);
    var response =
        mvc.perform(get("/api/v1/auth/me").cookie(actor))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.detail").value("Authentication is required"))
            .andExpect(jsonPath("$.account").doesNotExist())
            .andExpect(jsonPath("$.household").doesNotExist())
            .andReturn()
            .getResponse();
    verify(contextStore).findForAccount(a);
    assertThat(response.getContentAsString())
        .doesNotContain(a.toString(), homeA.toString(), a + "@example.test");
  }
}
