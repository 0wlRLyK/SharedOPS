package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.security.SessionPolicy;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HouseholdCreationHttpIT {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionRepository<? extends Session> sessions;
  UUID account;
  UsernamePasswordAuthenticationToken auth;

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
    account = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,?)",
        account,
        account + "@example.com",
        "hash");
    var principal = new AccountPrincipal(account, account + "@example.com", "hash", 0);
    auth =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
  }

  @AfterEach
  void cleanup() {
    var ids =
        jdbc.queryForList(
            "SELECT household_id FROM household_memberships WHERE account_id = ?",
            UUID.class,
            account);
    jdbc.update("DELETE FROM household_memberships WHERE account_id = ?", account);
    for (var id : ids) {
      jdbc.update("DELETE FROM households WHERE id = ?", id);
    }
    jdbc.update("DELETE FROM accounts WHERE id = ?", account);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
      String json) {
    return post("/api/v1/households")
        .cookie(authenticatedCookie(auth))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  @Test
  void createsWithDefaultZoneAndRejectsSecondHousehold() throws Exception {
    var result =
        mvc.perform(request("{\"name\":\"  My  hOme  \",\"currency\":\"EUR\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("My  hOme"))
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.timezone").value("Europe/Berlin"))
            .andExpect(jsonPath("$.icon").isEmpty())
            .andReturn();
    String responseId =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    UUID.fromString(responseId);
    assertThat(result.getResponse().getHeader("Location"))
        .isEqualTo("/api/v1/households/" + responseId);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_memberships WHERE account_id=? AND active",
                Integer.class,
                account))
        .isEqualTo(1);
    mvc.perform(request("{\"name\":\"Another\",\"currency\":\"EUR\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACTIVE_HOUSEHOLD_EXISTS"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_memberships WHERE account_id=? AND active",
                Integer.class,
                account))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"EUR", "UAH", "USD", "GBP", "PLN", "CHF", "CZK", "DKK", "NOK", "SEK"})
  void acceptsSupportedCurrency(String currency) throws Exception {
    mvc.perform(
            request(
                "{\"name\":\"Home\",\"currency\":\""
                    + currency
                    + "\",\"timezone\":\"Europe/Kyiv\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").value(currency))
        .andExpect(jsonPath("$.timezone").value("Europe/Kyiv"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"name\":\" \",\"currency\":\"EUR\"}",
        "{\"name\":\"Home\",\"currency\":\"eur\"}",
        "{\"name\":\"Home\",\"currency\":\"JPY\"}",
        "{\"name\":\"Home\",\"currency\":\"EUR\",\"timezone\":null}",
        "{\"name\":\"Home\",\"currency\":\"EUR\",\"timezone\":\"+02:00\"}",
        "{\"name\":\"Home\",\"currency\":\"EUR\",\"icon\":\" \"}"
      })
  void invalidInputLeavesCommittedStateUnchanged(String json) throws Exception {
    var before = jdbc.queryForList("SELECT * FROM households ORDER BY id");
    var memberships = jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id");
    mvc.perform(request(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.instance").doesNotExist());
    assertThat(jdbc.queryForList("SELECT * FROM households ORDER BY id")).isEqualTo(before);
    assertThat(jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id"))
        .isEqualTo(memberships);
  }

  @Test
  void acceptsSingleCodePointNameAndExplicitNullIcon() throws Exception {
    var result =
        mvc.perform(request("{\"name\":\"😀\",\"currency\":\"EUR\",\"icon\":null}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("😀"))
            .andExpect(jsonPath("$.icon").isEmpty())
            .andReturn();
    String location = result.getResponse().getHeader("Location");
    assertThat(location).isNotNull().startsWith("/api/v1/households/");
    UUID householdId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    var stored = jdbc.queryForMap("SELECT name, icon FROM households WHERE id = ?", householdId);
    assertThat(stored.get("name")).isEqualTo("😀");
    assertThat(stored.get("icon")).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_memberships WHERE account_id = ? AND household_id = ? AND active",
                Integer.class,
                account,
                householdId))
        .isEqualTo(1);
  }

  @Test
  void rejectsEmptyIconWithoutChangingCommittedHouseholdsOrMemberships() throws Exception {
    var beforeHouseholds = jdbc.queryForList("SELECT * FROM households ORDER BY id");
    var beforeMemberships = jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id");
    mvc.perform(request("{\"name\":\"Home\",\"currency\":\"EUR\",\"icon\":\"\"}"))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForList("SELECT * FROM households ORDER BY id"))
        .isEqualTo(beforeHouseholds);
    assertThat(jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id"))
        .isEqualTo(beforeMemberships);
  }

  @Test
  void unicodeNormalizationAndCodePointBoundaries() throws Exception {
    String max = "😀".repeat(100);
    mvc.perform(request("{\"name\":\"" + "😀".repeat(101) + "\",\"currency\":\"EUR\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            request(
                "{\"name\":\"\u00a0"
                    + max
                    + "\u00a0\",\"icon\":\"  icon  \",\"currency\":\"EUR\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value(max))
        .andExpect(jsonPath("$.icon").value("  icon  "));
  }

  @Test
  void rejectsOversizedIconAndMalformedInputWithoutWrites() throws Exception {
    var before = jdbc.queryForList("SELECT * FROM households ORDER BY id");
    mvc.perform(
            request(
                "{\"name\":\"Home\",\"currency\":\"EUR\",\"icon\":\"" + "x".repeat(101) + "\"}"))
        .andExpect(status().isBadRequest());
    String secret = "malformed-secret-" + UUID.randomUUID();
    var malformed =
        mvc.perform(request("{\"name\":\"" + secret + "\",not-json"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertThat(malformed.getResponse().getContentAsString()).doesNotContain(secret);
    assertThat(jdbc.queryForList("SELECT * FROM households ORDER BY id")).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_memberships WHERE account_id=?",
                Integer.class,
                account))
        .isZero();
  }

  @Test
  void authenticationAndCsrfAreEnforced() throws Exception {
    var body = "{\"name\":\"Home\",\"currency\":\"EUR\"}";
    mvc.perform(
            post("/api/v1/households")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/households")
                .cookie(authenticatedCookie(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_memberships WHERE account_id=?",
                Integer.class,
                account))
        .isZero();
  }
}
