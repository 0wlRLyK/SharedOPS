package dev.sharedops.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.web.http.CookieSerializer;

class SessionPolicyTest {
  @Test
  void expiresAtAbsoluteDeadlineRegardlessOfActivity() {
    MockHttpSession session = new MockHttpSession();
    Instant login = Instant.parse("2026-01-01T00:00:00Z");
    SessionPolicy.initialize(session, false, login);
    assertThat(session.getMaxInactiveInterval()).isEqualTo(1800);
    assertThat(SessionPolicy.expired(session, login.plusSeconds(28799))).isFalse();
    assertThat(SessionPolicy.expired(session, login.plusSeconds(28800))).isTrue();
  }

  @Test
  void persistentSessionUsesSevenDaysIdleAndThirtyDaysAbsolute() {
    MockHttpSession session = new MockHttpSession();
    Instant login = Instant.parse("2026-01-01T00:00:00Z");
    SessionPolicy.initialize(session, true, login);
    assertThat(session.getMaxInactiveInterval()).isEqualTo(604800);
    assertThat(SessionPolicy.expired(session, login.plusSeconds(2591999))).isFalse();
    assertThat(SessionPolicy.expired(session, login.plusSeconds(2592000))).isTrue();
  }

  @Test
  void cookieAttributesDistinguishPersistentAndLocalSessions() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    MockHttpServletRequest request = new MockHttpServletRequest();
    SessionPolicy.initialize(request.getSession(), true, now);
    MockHttpServletResponse response = new MockHttpServletResponse();
    new SessionCookieSerializer(true, Clock.fixed(now, ZoneOffset.UTC))
        .writeCookieValue(new CookieSerializer.CookieValue(request, response, "abc"));
    assertThat(response.getHeader("Set-Cookie"))
        .contains("Secure", "HttpOnly", "SameSite=Lax", "Max-Age=2592000");
    MockHttpServletRequest local = new MockHttpServletRequest();
    MockHttpServletResponse localResponse = new MockHttpServletResponse();
    new SessionCookieSerializer(false, Clock.fixed(now, ZoneOffset.UTC))
        .writeCookieValue(new CookieSerializer.CookieValue(local, localResponse, "abc"));
    assertThat(localResponse.getHeader("Set-Cookie"))
        .contains("HttpOnly", "SameSite=Lax")
        .doesNotContain("Secure", "Max-Age");
  }
}
