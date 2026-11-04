package dev.sharedops.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sharedops.SharedopsBackendApplication;
import dev.sharedops.common.api.ApiProblemFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.SecureProbeController.class)
@ContextConfiguration(classes = SharedopsBackendApplication.class)
@Import({
  SecurityConfig.class,
  ApiAuthenticationEntryPoint.class,
  ApiProblemFactory.class,
  ApiAccessDeniedHandler.class,
  SecurityConfigTest.AdminTestSecurityConfig.class,
  SecurityConfigTest.SecureProbeController.class
})
public class SecurityConfigTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void check401error() throws Exception {
    mockMvc
        .perform(get("/test/secure"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.detail").value("Authentication required"));
  }

  @Test
  @WithMockUser(roles = "USER")
  void check403error() throws Exception {
    mockMvc
        .perform(get("/test/admin"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.detail").value("Access denied"));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void shouldAllowAdminAccess() throws Exception {
    mockMvc
        .perform(get("/test/admin"))
        .andExpect(status().isOk())
        .andExpect(content().string("admin"));
  }

  @RestController
  static class SecureProbeController {

    @GetMapping("/test/secure")
    String secure() {
      return "ok";
    }

    @GetMapping("/test/admin")
    String admin() {
      return "admin";
    }
  }

  @TestConfiguration
  static class AdminTestSecurityConfig {
    @Order(1)
    @Bean
    SecurityFilterChain adminSecurityFilterChain(
        HttpSecurity http,
        ApiAuthenticationEntryPoint entryPoint,
        ApiAccessDeniedHandler deniedHandler)
        throws Exception {
      http.securityMatcher("/test/admin")
          .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
          .exceptionHandling(
              exceptions -> {
                exceptions.authenticationEntryPoint(entryPoint);
                exceptions.accessDeniedHandler(deniedHandler);
              })
          .sessionManagement(
              session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

      return http.build();
    }
  }
}
