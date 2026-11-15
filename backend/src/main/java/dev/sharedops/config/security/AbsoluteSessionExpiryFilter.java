package dev.sharedops.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class AbsoluteSessionExpiryFilter extends OncePerRequestFilter {
  private final Clock clock;

  public AbsoluteSessionExpiryFilter(Clock clock) {
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    HttpSession session = request.getSession(false);
    if (authentication != null
        && authentication.isAuthenticated()
        && (session == null || SessionPolicy.expired(session, clock.instant()))) {
      SecurityContextHolder.clearContext();
      if (session != null) {
        session.invalidate();
      }
    }
    chain.doFilter(request, response);
  }
}
