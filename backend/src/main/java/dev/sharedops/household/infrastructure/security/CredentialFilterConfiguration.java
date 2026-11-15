package dev.sharedops.household.infrastructure.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class CredentialFilterConfiguration {
  @Bean
  CredentialVersionFilter credentialVersionFilter(JdbcTemplate jdbc) {
    return new CredentialVersionFilter(jdbc);
  }

  @Bean
  FilterRegistrationBean<CredentialVersionFilter> credentialFilterRegistration(
      CredentialVersionFilter filter) {
    FilterRegistrationBean<CredentialVersionFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
