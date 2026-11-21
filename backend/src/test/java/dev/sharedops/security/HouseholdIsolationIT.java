package dev.sharedops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.security.SessionPolicy;
import dev.sharedops.household.application.GetHouseholdService;
import dev.sharedops.household.application.HouseholdNotFoundException;
import dev.sharedops.household.application.HouseholdStore;
import dev.sharedops.household.application.MembershipStore;
import dev.sharedops.household.application.UnauthenticatedHouseholdAccessException;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HouseholdIsolationIT {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionRepository<? extends Session> sessions;
  @Autowired GetHouseholdService service;
  @MockitoSpyBean MembershipStore memberships;
  @MockitoSpyBean HouseholdStore households;
  UUID a, b, homeA, homeB, absent;

  private <S extends Session> Cookie authenticatedCookie(
      SessionRepository<S> repository, Authentication authentication) {
    MockHttpSession policySession = new MockHttpSession();
    SessionPolicy.initialize(policySession, false, Instant.now());
    S session = repository.createSession();
    var names = policySession.getAttributeNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      session.setAttribute(name, policySession.getAttribute(name));
    }
    session.setMaxInactiveInterval(Duration.ofSeconds(policySession.getMaxInactiveInterval()));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    repository.save(session);
    return new Cookie(
        "SESSION",
        Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8)));
  }

  private Cookie authenticatedCookie(Authentication authentication) {
    return authenticatedCookie(sessions, authentication);
  }

  @BeforeEach
  void setup() {
    a = UUID.randomUUID();
    b = UUID.randomUUID();
    homeA = UUID.randomUUID();
    homeB = UUID.randomUUID();
    absent = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,?),(?,?,?)",
        a,
        a + "@example.com",
        "hash",
        b,
        b + "@example.com",
        "hash");
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home A', 'EUR', 'Europe/Berlin'), (?, 'Home B', 'USD', 'Europe/Berlin')",
        homeA,
        homeB);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active) VALUES (?,?,?,true),(?,?,?,true)",
        UUID.randomUUID(),
        a,
        homeA,
        UUID.randomUUID(),
        b,
        homeB);
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM household_memberships WHERE account_id IN (?,?)", a, b);
    jdbc.update("DELETE FROM households WHERE id IN (?,?)", homeA, homeB);
    jdbc.update("DELETE FROM accounts WHERE id IN (?,?)", a, b);
  }

  private UsernamePasswordAuthenticationToken auth(UUID actor) {
    var principal = new AccountPrincipal(actor, actor + "@example.com", "hash", 0);
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, null, principal.getAuthorities());
  }

  private Object snapshot() {
    return java.util.List.of(
        jdbc.queryForList("SELECT * FROM households ORDER BY id"),
        jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id"));
  }

  @Test
  void ownerReadsAndForeignEqualsAbsentWithoutStateChange() throws Exception {
    var before = snapshot();
    mvc.perform(get("/api/v1/households/{id}", homeA).cookie(authenticatedCookie(auth(a))))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"id":"%s","name":"Home A","icon":null,"currency":"EUR","timezone":"Europe/Berlin"}
                    """
                        .formatted(homeA),
                    JsonCompareMode.STRICT));
    var foreign =
        mvc.perform(get("/api/v1/households/{id}", homeB).cookie(authenticatedCookie(auth(a))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Resource not found."))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.instance").doesNotExist())
            .andReturn()
            .getResponse();
    var missing =
        mvc.perform(get("/api/v1/households/{id}", absent).cookie(authenticatedCookie(auth(a))))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse();
    assertThat(foreign.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(missing.getContentType()).isEqualTo(foreign.getContentType());
    assertThat(missing.getContentAsString()).isEqualTo(foreign.getContentAsString());
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"foreign", "absent", "inactive", "missing"})
  void rejectsEachUnauthorizedScenario(String scenario) throws Exception {
    if (scenario.equals("inactive")) {
      jdbc.update("UPDATE household_memberships SET active=false WHERE account_id=?", a);
    }
    if (scenario.equals("missing")) {
      jdbc.update("DELETE FROM household_memberships WHERE account_id=?", a);
    }
    UUID target = scenario.equals("foreign") ? homeB : scenario.equals("absent") ? absent : homeA;
    var before = snapshot();
    var denied =
        mvc.perform(get("/api/v1/households/{id}", target).cookie(authenticatedCookie(auth(a))))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(
                content()
                    .json(
                        """
                        {"title":"Not Found","status":404,"detail":"Resource not found.","code":"NOT_FOUND"}
                        """,
                        JsonCompareMode.STRICT))
            .andReturn()
            .getResponse();
    assertThat(denied.getContentAsString())
        .doesNotContain(a.toString(), b.toString(), homeA.toString(), homeB.toString());
    SecurityContextHolder.getContext().setAuthentication(auth(a));
    assertThatThrownBy(() -> service.get(target)).isInstanceOf(HouseholdNotFoundException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void directNoIdentityFailsBeforeReadsAndServerActorControlsScope() {
    var before = snapshot();
    SecurityContextHolder.clearContext();
    clearInvocations(memberships, households);
    assertThatThrownBy(() -> service.get(homeA))
        .isInstanceOf(UnauthenticatedHouseholdAccessException.class);
    verifyNoInteractions(memberships, households);
    SecurityContextHolder.getContext().setAuthentication(auth(a));
    assertThat(service.get(homeA).id()).isEqualTo(homeA);
    assertThatThrownBy(() -> service.get(homeB)).isInstanceOf(HouseholdNotFoundException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void malformedIdDoesNotEchoInputAndUnauthenticatedIsDenied() throws Exception {
    var before = snapshot();
    var result =
        mvc.perform(get("/api/v1/households/not-a-uuid").cookie(authenticatedCookie(auth(a))))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.instance").doesNotExist())
            .andReturn()
            .getResponse();
    assertThat(result.getContentAsString()).doesNotContain("not-a-uuid");
    mvc.perform(get("/api/v1/households/{id}", homeA)).andExpect(status().isUnauthorized());
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void concurrentActorsDoNotCrossScopes() throws Exception {
    var before = snapshot();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one = pool.submit(() -> checkActor(a, homeA, homeB, ready, start));
      var two = pool.submit(() -> checkActor(b, homeB, homeA, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(one.get(10, TimeUnit.SECONDS)).isTrue();
      assertThat(two.get(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      start.countDown();
    }
    assertThat(snapshot()).isEqualTo(before);
  }

  private boolean checkActor(
      UUID actor, UUID own, UUID foreign, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    try {
      SecurityContextHolder.getContext().setAuthentication(auth(actor));
      ready.countDown();
      if (!start.await(10, TimeUnit.SECONDS)) {
        return false;
      }
      for (int i = 0; i < 20; i++) {
        if (!service.get(own).id().equals(own)) {
          return false;
        }
        try {
          service.get(foreign);
          return false;
        } catch (HouseholdNotFoundException expected) {
          /* expected denial */
        }
      }
      return true;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
