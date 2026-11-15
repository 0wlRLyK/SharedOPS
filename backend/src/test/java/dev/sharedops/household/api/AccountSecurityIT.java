package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.api.dto.CsrfTokenResponse;
import dev.sharedops.household.application.RegisterAccountService;
import dev.sharedops.household.domain.Account;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
class AccountSecurityIT {
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired MockMvc mockMvc;

  @Autowired JsonMapper jsonMapper;
  @Autowired RegisterAccountService accountService;

  @Test
  void normalizedEmailIsValidatedBeforeRegistrationAndLogin() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
    CsrfTokenResponse token =
        jsonMapper.readValue(csrf.getResponse().getContentAsString(), CsrfTokenResponse.class);
    Cookie cookie = csrf.getResponse().getCookie("SESSION");
    String body =
        jsonMapper.writeValueAsString(
            java.util.Map.of(
                "email",
                "  " + email.toUpperCase(java.util.Locale.ROOT) + "  ",
                "password",
                "password"));
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .cookie(cookie)
                .header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email));
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .cookie(cookie)
                .header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email));
  }

  @Test
  void oversizedEmailReturnsValidationError() throws Exception {
    MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
    CsrfTokenResponse token =
        jsonMapper.readValue(csrf.getResponse().getContentAsString(), CsrfTokenResponse.class);
    String email = "a".repeat(60) + "@" + ("b".repeat(60) + ".").repeat(3) + "example.com";
    String body =
        jsonMapper.writeValueAsString(java.util.Map.of("email", email, "password", "password"));
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .cookie(csrf.getResponse().getCookie("SESSION"))
                .header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void failedLoginReturnsStructuredUnauthorized() throws Exception {
    MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
    CsrfTokenResponse token =
        jsonMapper.readValue(csrf.getResponse().getContentAsString(), CsrfTokenResponse.class);
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .cookie(csrf.getResponse().getCookie("SESSION"))
                .header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.com\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void passwordChangeRevokesCurrentSessionAndRequiresNewPassword() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    accountService.register(email, " old password ");
    MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
    CsrfTokenResponse token =
        jsonMapper.readValue(csrf.getResponse().getContentAsString(), CsrfTokenResponse.class);
    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .cookie(csrf.getResponse().getCookie("SESSION"))
                    .header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\" old password \"}"))
            .andExpect(status().isOk())
            .andReturn();
    Cookie session = login.getResponse().getCookie("SESSION");
    MvcResult nextCsrf = mockMvc.perform(get("/api/v1/auth/csrf").cookie(session)).andReturn();
    CsrfTokenResponse nextToken =
        jsonMapper.readValue(nextCsrf.getResponse().getContentAsString(), CsrfTokenResponse.class);
    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .cookie(session)
                .header(nextToken.headerName(), nextToken.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\" old password \",\"newPassword\":\" new password \"}"))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .cookie(session)
                .header(nextToken.headerName(), nextToken.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\" old password \",\"newPassword\":\" new password \"}"))
        .andExpect(status().isForbidden());
    MvcResult freshCsrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
    CsrfTokenResponse freshToken =
        jsonMapper.readValue(freshCsrf.getResponse().getContentAsString(), CsrfTokenResponse.class);
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .cookie(freshCsrf.getResponse().getCookie("SESSION"))
                .header(freshToken.headerName(), freshToken.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\" new password \"}"))
        .andExpect(status().isOk());
  }

  @Test
  void anonymousUserCanObtainCsrfToken() throws Exception {

    mockMvc
        .perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headerName").isNotEmpty())
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void registrationWithoutCsrfIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {
                  "email": "test@example.com",
                  "password": "TestPassword123!"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Access denied"))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void registrationWithCsrfIsAllowed() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    try {
      MvcResult csrfResult =
          mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
      CsrfTokenResponse csrfResponse =
          jsonMapper.readValue(
              csrfResult.getResponse().getContentAsString(), CsrfTokenResponse.class);
      Cookie sessionCookie = csrfResult.getResponse().getCookie("SESSION");
      assertThat(sessionCookie).isNotNull();
      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .cookie(sessionCookie)
                  .header(csrfResponse.headerName(), csrfResponse.token())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
              {
                "email": "%s",
                "password": "TestPassword123!"
              }
              """
                          .formatted(email)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.email").value(email))
          .andExpect(jsonPath("$.id").isNotEmpty());
    } finally {
      jdbcTemplate.update("DELETE FROM accounts WHERE email = ?", email);
    }
  }

  @Test
  void successfulLoginRotatesSessionId() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    try {

      Account createdAccount = accountService.register(email, "TestPassword123!");

      MvcResult csrfResult =
          mockMvc
              .perform(get("/api/v1/auth/csrf"))
              .andExpect(status().isOk())
              .andExpect(unauthenticated())
              .andReturn();
      CsrfTokenResponse csrfResponse =
          jsonMapper.readValue(
              csrfResult.getResponse().getContentAsString(), CsrfTokenResponse.class);
      Cookie sessionCookie = csrfResult.getResponse().getCookie("SESSION");
      assertThat(sessionCookie).isNotNull();
      MvcResult loginResult =
          mockMvc
              .perform(
                  post("/api/v1/auth/login")
                      .cookie(sessionCookie)
                      .header(csrfResponse.headerName(), csrfResponse.token())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
      {
        "email": "%s",
        "password": "TestPassword123!"
      }
      """
                              .formatted(email)))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.email").value(email))
              .andExpect(jsonPath("$.id").value(createdAccount.getId().id().toString()))
              .andReturn();

      Cookie newSessionCookie = loginResult.getResponse().getCookie("SESSION");

      assertThat(newSessionCookie).isNotNull();

      assertThat(newSessionCookie.getValue()).isNotEqualTo(sessionCookie.getValue());

      mockMvc
          .perform(get("/api/v1/auth/csrf").cookie(newSessionCookie))
          .andExpect(status().isOk())
          .andReturn();

    } finally {
      jdbcTemplate.update("DELETE FROM accounts WHERE email = ?", email);
    }
  }

  @Test
  void successfulLoginRotatesCSRF() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    try {

      Account createdAccount = accountService.register(email, "TestPassword123!");

      MvcResult csrfResult =
          mockMvc
              .perform(get("/api/v1/auth/csrf"))
              .andExpect(status().isOk())
              .andExpect(unauthenticated())
              .andReturn();
      CsrfTokenResponse csrfResponse =
          jsonMapper.readValue(
              csrfResult.getResponse().getContentAsString(), CsrfTokenResponse.class);
      Cookie sessionCookie = csrfResult.getResponse().getCookie("SESSION");
      assertThat(sessionCookie).isNotNull();
      MvcResult loginResult =
          mockMvc
              .perform(
                  post("/api/v1/auth/login")
                      .cookie(sessionCookie)
                      .header(csrfResponse.headerName(), csrfResponse.token())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
      {
        "email": "%s",
        "password": "TestPassword123!"
      }
      """
                              .formatted(email)))
              .andExpect(status().isOk())
              .andReturn();

      Cookie newSessionCookie = loginResult.getResponse().getCookie("SESSION");
      assertThat(newSessionCookie).isNotNull();

      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .cookie(newSessionCookie)
                  .header(csrfResponse.headerName(), csrfResponse.token())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
"""
{
"email": "%s",
"password": "TestPassword123!"
}
"""
                          .formatted(email)))
          .andExpect(status().isForbidden());

      MvcResult csrfResultPostAuth =
          mockMvc
              .perform(get("/api/v1/auth/csrf").cookie(newSessionCookie))
              .andExpect(status().isOk())
              .andReturn();
      CsrfTokenResponse csrfResponsePostAuth =
          jsonMapper.readValue(
              csrfResultPostAuth.getResponse().getContentAsString(), CsrfTokenResponse.class);

      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .cookie(newSessionCookie)
                  .header(csrfResponsePostAuth.headerName(), csrfResponsePostAuth.token())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
"""
{
"email": "%s",
"password": "TestPassword123!"
}
"""
                          .formatted(email)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

    } finally {
      jdbcTemplate.update("DELETE FROM accounts WHERE email = ?", email);
    }
  }

  @Test
  void logoutInvalidatesCurrentSession() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    try {

      Account createdAccount = accountService.register(email, "TestPassword123!");

      MvcResult csrfResult =
          mockMvc
              .perform(get("/api/v1/auth/csrf"))
              .andExpect(status().isOk())
              .andExpect(unauthenticated())
              .andReturn();
      CsrfTokenResponse csrfResponse =
          jsonMapper.readValue(
              csrfResult.getResponse().getContentAsString(), CsrfTokenResponse.class);
      Cookie sessionCookie = csrfResult.getResponse().getCookie("SESSION");
      assertThat(sessionCookie).isNotNull();
      MvcResult loginResult =
          mockMvc
              .perform(
                  post("/api/v1/auth/login")
                      .cookie(sessionCookie)
                      .header(csrfResponse.headerName(), csrfResponse.token())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
"""
{
"email": "%s",
"password": "TestPassword123!"
}
"""
                              .formatted(email)))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.email").value(email))
              .andExpect(jsonPath("$.id").value(createdAccount.getId().id().toString()))
              .andReturn();

      Cookie newSessionCookie = loginResult.getResponse().getCookie("SESSION");

      assertThat(newSessionCookie).isNotNull();

      assertThat(newSessionCookie.getValue()).isNotEqualTo(sessionCookie.getValue());

      MvcResult csrfResultPostAuth =
          mockMvc
              .perform(get("/api/v1/auth/csrf").cookie(newSessionCookie))
              .andExpect(status().isOk())
              .andReturn();
      CsrfTokenResponse csrfResponsePostAuth =
          jsonMapper.readValue(
              csrfResultPostAuth.getResponse().getContentAsString(), CsrfTokenResponse.class);

      mockMvc
          .perform(
              post("/api/v1/auth/logout")
                  .cookie(newSessionCookie)
                  .header(csrfResponsePostAuth.headerName(), csrfResponsePostAuth.token()))
          .andExpect(status().isNoContent());

      mockMvc
          .perform(get("/api/v1/__auth_probe").cookie(newSessionCookie))
          .andExpect(status().isUnauthorized());

    } finally {
      jdbcTemplate.update("DELETE FROM accounts WHERE email = ?", email);
    }
  }
}
