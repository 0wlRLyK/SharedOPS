package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.api.dto.CsrfTokenResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordLengthIT {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;

  @Test
  void bcryptBoundaryAndPrefixBypass() throws Exception {
    var initial = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
    var cookie = initial.getResponse().getCookie("SESSION");
    var token = json.readValue(initial.getResponse().getContentAsString(), CsrfTokenResponse.class);
    String email = UUID.randomUUID() + "@example.com";
    String password = "a".repeat(72);
    mvc.perform(
            post("/api/v1/auth/register")
                .cookie(cookie)
                .header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email, password + "x")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/auth/register")
                .cookie(cookie)
                .header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email, "é".repeat(37))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/auth/register")
                .cookie(cookie)
                .header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email, password)))
        .andExpect(status().isCreated());
    var failed =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(cookie)
                    .header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(email, password + "x")))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(failed.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS");
    var login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(cookie)
                    .header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(email, password)))
            .andExpect(status().isOk())
            .andReturn();
    var session = login.getResponse().getCookie("SESSION");
    var csrf = mvc.perform(get("/api/v1/auth/csrf").cookie(session)).andReturn();
    var fresh = json.readValue(csrf.getResponse().getContentAsString(), CsrfTokenResponse.class);
    mvc.perform(
            post("/api/v1/auth/password")
                .cookie(session)
                .header(fresh.headerName(), fresh.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\""
                        + password
                        + "\",\"newPassword\":\""
                        + "é".repeat(37)
                        + "\"}"))
        .andExpect(status().isBadRequest());
    var badCurrent =
        mvc.perform(
                post("/api/v1/auth/password")
                    .cookie(session)
                    .header(fresh.headerName(), fresh.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"currentPassword\":\""
                            + password
                            + "x\",\"newPassword\":\"new password\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(badCurrent.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS");
    mvc.perform(
            post("/api/v1/auth/password")
                .cookie(session)
                .header(fresh.headerName(), fresh.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\""
                        + password
                        + "\",\"newPassword\":\""
                        + "é".repeat(36)
                        + "\"}"))
        .andExpect(status().isNoContent());
  }

  private String body(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }
}
