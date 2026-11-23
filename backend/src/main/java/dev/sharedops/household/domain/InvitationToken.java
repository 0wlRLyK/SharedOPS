package dev.sharedops.household.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/** A bearer secret; its value must only be exposed when issuing an invitation. */
public final class InvitationToken {
  private static final int TOKEN_BYTES = 32;
  private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
  private static final SecureRandom RANDOM = new SecureRandom();
  private final String value;

  private InvitationToken(String value) {
    this.value = value;
  }

  /** Creates a new 256-bit random bearer token. */
  public static InvitationToken generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return new InvitationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
  }

  /** Parses the canonical unpadded base64url representation of a 256-bit token. */
  public static InvitationToken parse(String value) {
    if (value == null || !TOKEN_FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid invitation token");
    }
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(value);
      if (decoded.length != TOKEN_BYTES
          || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) {
        throw new IllegalArgumentException("Invalid invitation token");
      }
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("Invalid invitation token");
    }
    return new InvitationToken(value);
  }

  /** Returns the bearer secret for the one-time creation response. Never log this value. */
  public String reveal() {
    return value;
  }

  /** Computes the digest stored and looked up in place of the bearer secret. */
  public byte[] digest() {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }

  @Override
  public String toString() {
    return "InvitationToken[REDACTED]";
  }
}
