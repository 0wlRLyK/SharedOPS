package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.api.dto.CsrfTokenResponse;
import dev.sharedops.household.application.AuthenticatedAccount;
import dev.sharedops.household.application.RegisterAccountService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ConcurrentIdentityIT.Probe.class})
class ConcurrentIdentityIT {
  @Autowired MockMvc mvc;
  @Autowired JsonMapper mapper;
  @Autowired RegisterAccountService accounts;

  @RestController
  static class Probe {
    @GetMapping("/test/current-identity")
    String identity(Authentication authentication) {
      return ((AuthenticatedAccount) authentication.getPrincipal()).accountId().toString();
    }
  }

  @Test
  void interleavedAndConcurrentRequestsKeepEachAccountsIdentity() throws Exception {
    String email1 = UUID.randomUUID() + "@example.com";
    String email2 = UUID.randomUUID() + "@example.com";
    String id1 = accounts.register(email1, "password 1").getId().id().toString();
    String id2 = accounts.register(email2, "password 2").getId().id().toString();
    Cookie session1 = login(email1, "password 1");
    Cookie session2 = login(email2, "password 2");
    assertThat(identity(session1)).isEqualTo(id1);
    assertThat(identity(session2)).isEqualTo(id2);
    assertThat(identity(session1)).isEqualTo(id1);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                for (int i = 0; i < 20; i++) {
                  assertThat(identity(session1)).isEqualTo(id1);
                }
                return null;
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                for (int i = 0; i < 20; i++) {
                  assertThat(identity(session2)).isEqualTo(id2);
                }
                return null;
              });
      start.countDown();
      first.get(20, TimeUnit.SECONDS);
      second.get(20, TimeUnit.SECONDS);
    }
  }

  @Test
  void concurrentPasswordChangesApplyToTheirOwnAccounts() throws Exception {
    String firstEmail = UUID.randomUUID() + "@example.com";
    String secondEmail = UUID.randomUUID() + "@example.com";
    accounts.register(firstEmail, "old password 1");
    accounts.register(secondEmail, "old password 2");
    Cookie first = login(firstEmail, "old password 1");
    Cookie second = login(secondEmail, "old password 2");
    CsrfTokenResponse firstToken = token(first);
    CsrfTokenResponse secondToken = token(second);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one =
          executor.submit(
              () -> {
                start.await();
                changePassword(first, firstToken, "old password 1", "new password 1");
                return null;
              });
      var two =
          executor.submit(
              () -> {
                start.await();
                changePassword(second, secondToken, "old password 2", "new password 2");
                return null;
              });
      start.countDown();
      one.get(20, TimeUnit.SECONDS);
      two.get(20, TimeUnit.SECONDS);
    }
    login(firstEmail, "new password 1");
    login(secondEmail, "new password 2");
  }

  private CsrfTokenResponse token(Cookie cookie) throws Exception {
    var result =
        mvc.perform(get("/api/v1/auth/csrf").cookie(cookie)).andExpect(status().isOk()).andReturn();
    return mapper.readValue(result.getResponse().getContentAsString(), CsrfTokenResponse.class);
  }

  private void changePassword(
      Cookie cookie, CsrfTokenResponse token, String oldPassword, String newPassword)
      throws Exception {
    String body =
        mapper.writeValueAsString(
            java.util.Map.of("currentPassword", oldPassword, "newPassword", newPassword));
    mvc.perform(
            post("/api/v1/auth/password")
                .cookie(cookie)
                .header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());
  }

  private Cookie login(String email, String password) throws Exception {
    MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    CsrfTokenResponse token =
        mapper.readValue(csrf.getResponse().getContentAsString(), CsrfTokenResponse.class);
    return mvc.perform(
            post("/api/v1/auth/login")
                .cookie(csrf.getResponse().getCookie("SESSION"))
                .header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("SESSION");
  }

  private String identity(Cookie cookie) throws Exception {
    return mvc.perform(get("/test/current-identity").cookie(cookie))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
