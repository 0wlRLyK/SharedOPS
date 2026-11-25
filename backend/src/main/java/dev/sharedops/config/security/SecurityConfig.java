package dev.sharedops.config.security;

import dev.sharedops.household.infrastructure.security.CredentialVersionFilter;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;

@Configuration
public class SecurityConfig {
  @Bean
  CookieSerializer cookieSerializer(
      @Value("${sharedops.session.cookie.secure:true}") boolean secure, Clock clock) {
    return new SessionCookieSerializer(secure, clock);
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SessionAuthenticationStrategy sessionAuthenticationStrategy(
      CsrfTokenRepository csrfTokenRepository) {
    ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy =
        new ChangeSessionIdAuthenticationStrategy();
    CsrfAuthenticationStrategy csrfAuthenticationStrategy =
        new CsrfAuthenticationStrategy(csrfTokenRepository);

    return new CompositeSessionAuthenticationStrategy(
        List.of(changeSessionIdAuthenticationStrategy, csrfAuthenticationStrategy));
  }

  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    return new HttpSessionCsrfTokenRepository();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      Clock clock,
      ObjectProvider<CredentialVersionFilter> versionFilter,
      ApiAuthenticationEntryPoint entryPoint,
      ApiAccessDeniedHandler deniedHandler,
      SecurityContextRepository securityContextRepository,
      CsrfTokenRepository csrfTokenRepository)
      throws Exception {

    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions -> {
              exceptions.authenticationEntryPoint(entryPoint);
              exceptions.accessDeniedHandler(deniedHandler);
            })
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .securityContext(security -> security.securityContextRepository(securityContextRepository));

    http.addFilterBefore(new CurrentContextNoStoreFilter(), SecurityContextHolderFilter.class);
    http.addFilterAfter(new AbsoluteSessionExpiryFilter(clock), SecurityContextHolderFilter.class);
    CredentialVersionFilter filter = versionFilter.getIfAvailable();
    if (filter != null) {
      http.addFilterAfter(filter, AbsoluteSessionExpiryFilter.class);
    }
    http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository));

    http.logout(
        logout ->
            logout
                .logoutUrl("/api/v1/auth/logout")
                .logoutSuccessHandler(
                    new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));

    return http.build();
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }
}
