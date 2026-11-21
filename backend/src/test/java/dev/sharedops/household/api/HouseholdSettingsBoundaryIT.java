package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.junit.jupiter.params.provider.CsvSource;
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
class HouseholdSettingsBoundaryIT {
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
  void setUp() {
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
  void cleanUp() {
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

  private void create(String json) throws Exception {
    mvc.perform(
            post("/api/v1/households")
                .cookie(authenticatedCookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());
  }

  @ParameterizedTest
  @ValueSource(strings = {"EUR", "UAH", "USD", "GBP", "PLN", "CHF", "CZK", "DKK", "NOK", "SEK"})
  void supportedCurrencyPersistsWithDefaultTimezone(String currency) throws Exception {
    create("{\"name\":\"Home\",\"currency\":\"" + currency + "\"}");
    var row =
        jdbc.queryForMap(
            "SELECT h.currency, h.timezone FROM households h JOIN household_memberships m ON m.household_id = h.id WHERE m.account_id = ? AND m.active",
            account);
    assertThat(row.get("currency")).isEqualTo(currency);
    assertThat(row.get("timezone")).isEqualTo("Europe/Berlin");
  }

  @ParameterizedTest
  @CsvSource({"Europe/Kyiv", "America/New_York", "Asia/Tokyo"})
  void chosenIanaZonePersists(String timezone) throws Exception {
    create("{\"name\":\"Home\",\"currency\":\"EUR\",\"timezone\":\"" + timezone + "\"}");
    assertThat(
            jdbc.queryForObject(
                "SELECT h.timezone FROM households h JOIN household_memberships m ON m.household_id = h.id WHERE m.account_id = ? AND m.active",
                String.class,
                account))
        .isEqualTo(timezone);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"name\":\"Home\"}",
        "{\"name\":\"Home\",\"currency\":null}",
        "{\"name\":\"Home\",\"currency\":\"\"}",
        "{\"name\":\"Home\",\"currency\":\"eur\"}",
        "{\"name\":\"Home\",\"currency\":\"JPY\"}",
        "{\"name\":\"Home\",\"currency\":\"EUR\",\"timezone\":null}",
        "{\"name\":\"Home\",\"currency\":\"EUR\",\"timezone\":\"\"}",
        "{\"name\":\"Home\",\"currency\":\"EUR\",\"timezone\":\"+02:00\"}",
        "{\"name\":\"Home\",\"currency\":\"EUR\",\"timezone\":\"Mars/Olympus\"}"
      })
  void invalidSettingsLeaveCommittedHouseholdsAndMembershipsUnchanged(String json)
      throws Exception {
    var householdsBefore = jdbc.queryForList("SELECT * FROM households ORDER BY id");
    var membershipsBefore = jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id");
    mvc.perform(
            post("/api/v1/households")
                .cookie(authenticatedCookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForList("SELECT * FROM households ORDER BY id"))
        .isEqualTo(householdsBefore);
    assertThat(jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id"))
        .isEqualTo(membershipsBefore);
  }

  @Test
  void unsupportedSettingsMutationsCannotChangePersistedState() throws Exception {
    create("{\"name\":\"Home\",\"currency\":\"EUR\",\"timezone\":\"Europe/Berlin\"}");
    UUID household =
        jdbc.queryForObject(
            "SELECT household_id FROM household_memberships WHERE account_id = ? AND active",
            UUID.class,
            account);
    var householdsBefore = jdbc.queryForList("SELECT * FROM households ORDER BY id");
    var membershipsBefore = jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id");
    String body = "{\"currency\":\"USD\",\"timezone\":\"Asia/Tokyo\"}";
    int putStatus =
        mvc.perform(
                put("/api/v1/households/{id}", household)
                    .cookie(authenticatedCookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(putStatus).isBetween(400, 499);
    assertThat(jdbc.queryForList("SELECT * FROM households ORDER BY id"))
        .isEqualTo(householdsBefore);
    assertThat(jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id"))
        .isEqualTo(membershipsBefore);
    int patchStatus =
        mvc.perform(
                patch("/api/v1/households/{id}", household)
                    .cookie(authenticatedCookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(patchStatus).isBetween(400, 499);
    assertThat(jdbc.queryForList("SELECT * FROM households ORDER BY id"))
        .isEqualTo(householdsBefore);
    assertThat(jdbc.queryForList("SELECT * FROM household_memberships ORDER BY id"))
        .isEqualTo(membershipsBefore);
  }
}
