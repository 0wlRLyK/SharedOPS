package dev.sharedops.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies to security rejections as well as successful current-context responses. */
final class CurrentContextNoStoreFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if ("GET".equals(request.getMethod())
        && (request.getContextPath() + "/api/v1/auth/me").equals(request.getRequestURI())) {
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
    chain.doFilter(request, response);
  }
}
