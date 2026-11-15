package dev.sharedops.config.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

public final class SessionCookieSerializer implements CookieSerializer {
  private final DefaultCookieSerializer delegate = new DefaultCookieSerializer();
  private final Clock clock;

  public SessionCookieSerializer(boolean secure, Clock clock) {
    this.clock = clock;
    delegate.setUseSecureCookie(secure);
    delegate.setUseHttpOnlyCookie(true);
    delegate.setSameSite("Lax");
  }

  @Override
  public List<String> readCookieValues(HttpServletRequest request) {
    return delegate.readCookieValues(request);
  }

  @Override
  public void writeCookieValue(CookieValue cookieValue) {
    if (cookieValue.getCookieMaxAge() < 0) {
      cookieValue.setCookieMaxAge(
          SessionPolicy.cookieMaxAge(cookieValue.getRequest().getSession(false), clock.instant()));
    }
    delegate.writeCookieValue(cookieValue);
  }
}
