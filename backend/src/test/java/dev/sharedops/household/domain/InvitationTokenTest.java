package dev.sharedops.household.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class InvitationTokenTest {
  @Test
  void generatesCanonicalRandom256BitTokens() {
    InvitationToken first = InvitationToken.generate();
    InvitationToken second = InvitationToken.generate();

    assertThat(first.reveal()).matches("[A-Za-z0-9_-]{43}").doesNotContain("=");
    assertThat(Base64.getUrlDecoder().decode(first.reveal())).hasSize(32);
    assertThat(second.reveal()).isNotEqualTo(first.reveal());
    assertThat(InvitationToken.parse(first.reveal()).reveal()).isEqualTo(first.reveal());
  }

  @Test
  void digestIsSha256OfTheBearerAndDoesNotExposeMutableState() throws Exception {
    InvitationToken token = InvitationToken.generate();
    byte[] expected =
        MessageDigest.getInstance("SHA-256")
            .digest(token.reveal().getBytes(StandardCharsets.US_ASCII));

    assertThat(token.digest()).hasSize(32).containsExactly(expected);
    assertThat(InvitationToken.parse(token.reveal()).digest()).containsExactly(expected);
    byte[] changed = token.digest();
    changed[0] ^= 1;
    assertThat(token.digest()).containsExactly(expected);
  }

  @Test
  void rejectsMalformedAndNoncanonicalInputWithoutEchoingIt() {
    String noncanonical = "A".repeat(42) + "B";
    for (String input :
        new String[] {
          null,
          "",
          "A".repeat(42),
          "A".repeat(44),
          "A".repeat(42) + "+",
          "A".repeat(42) + "=",
          noncanonical
        }) {
      assertThatThrownBy(() -> InvitationToken.parse(input))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid invitation token")
          .hasNoCause();
    }
  }

  @Test
  void stringRepresentationRedactsTheBearer() {
    InvitationToken token = InvitationToken.generate();

    assertThat(token.toString()).contains("REDACTED").doesNotContain(token.reveal());
    assertThat(InvitationToken.parse(token.reveal()).toString()).doesNotContain(token.reveal());
  }
}
