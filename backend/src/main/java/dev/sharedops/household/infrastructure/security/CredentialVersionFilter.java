package dev.sharedops.household.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CredentialVersionFilter extends OncePerRequestFilter {
  private final JdbcTemplate jdbc;

  public CredentialVersionFilter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
      var versions =
          jdbc.query(
              "SELECT credential_version FROM accounts WHERE id = ?",
              (rs, n) -> rs.getLong(1),
              principal.accountId());
      if (principal.credentialVersion() == null
          || versions.isEmpty()
          || !principal.credentialVersion().equals(versions.get(0))) {
        var session = request.getSession(false);
        if (session != null) {
          session.invalidate();
        }
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
