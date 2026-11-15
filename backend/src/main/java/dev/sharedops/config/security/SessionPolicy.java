package dev.sharedops.config.security;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;

public final class SessionPolicy {
  private static final String DEADLINE = SessionPolicy.class.getName() + ".deadline";
  private static final String PERSISTENT = SessionPolicy.class.getName() + ".persistent";

  private SessionPolicy() {}

  public static void initialize(HttpSession session, boolean persistent, Instant now) {
    session.setMaxInactiveInterval(persistent ? 604800 : 1800);
    session.setAttribute(DEADLINE, now.plusSeconds(persistent ? 2592000 : 28800).toEpochMilli());
    session.setAttribute(PERSISTENT, persistent);
  }

  public static boolean expired(HttpSession session, Instant now) {
    Object deadline = session.getAttribute(DEADLINE);
    return !(deadline instanceof Long value) || now.toEpochMilli() >= value;
  }

  public static int cookieMaxAge(HttpSession session, Instant now) {
    if (session == null || !Boolean.TRUE.equals(session.getAttribute(PERSISTENT))) {
      return -1;
    }
    Object deadline = session.getAttribute(DEADLINE);
    if (!(deadline instanceof Long value)) {
      return -1;
    }
    return (int) Math.max(0, Math.min(2592000, (value - now.toEpochMilli()) / 1000));
  }
}
