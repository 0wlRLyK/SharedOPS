package dev.sharedops.household.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.application.InvitationStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvitationPersistenceIT {
  @Autowired InvitationStore invitations;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transactions;

  private final UUID household = UUID.randomUUID();
  private final UUID otherHousehold = UUID.randomUUID();
  private final UUID invitation = UUID.randomUUID();
  private final Instant created = Instant.parse("2026-01-01T12:00:00Z");
  private final byte[] digest = new byte[32];

  @BeforeEach
  void setUp() {
    digest[0] = 42;
    jdbc.update(
        "INSERT INTO households (id, name, currency, timezone) VALUES (?, 'A', 'EUR', 'Europe/Berlin'), (?, 'B', 'EUR', 'Europe/Berlin')",
        household,
        otherHousehold);
  }

  @AfterEach
  void tearDown() {
    jdbc.update(
        "DELETE FROM household_invitations WHERE household_id IN (?, ?)",
        household,
        otherHousehold);
    jdbc.update("DELETE FROM households WHERE id IN (?, ?)", household, otherHousehold);
  }

  @Test
  void storesDigestAndReadsOnlyTheInvitationBelongingToLockedHousehold() {
    Instant expiry = created.plus(24, ChronoUnit.HOURS);
    transactions.executeWithoutResult(
        status -> {
          assertThat(invitations.lockHousehold(household)).isTrue();
          invitations.insert(invitation, household, digest, created, expiry);
          assertThat(invitations.findByDigest(digest))
              .contains(new InvitationStore.Locator(invitation, household));
          assertThat(invitations.lockInvitation(otherHousehold, invitation)).isEmpty();
          assertThat(invitations.lockInvitation(household, invitation))
              .hasValueSatisfying(
                  state -> {
                    assertThat(state.expiresAt()).isEqualTo(expiry);
                    assertThat(state.consumedAt()).isNull();
                    assertThat(state.revokedAt()).isNull();
                  });
          assertThat(invitations.activeMemberCount(household)).isZero();
        });
    assertThat(
            jdbc.queryForObject(
                "SELECT token_hash FROM household_invitations WHERE id = ?",
                byte[].class,
                invitation))
        .isEqualTo(digest);
    assertThat(invitations.lockHousehold(UUID.randomUUID())).isFalse();
  }

  @Test
  void terminalUpdatesAreExclusiveAndConsumeRejectsExpiredInvitation() {
    Instant expiry = created.plus(24, ChronoUnit.HOURS);
    UUID expired = UUID.randomUUID();
    byte[] otherDigest = digest.clone();
    otherDigest[0]++;
    invitations.insert(invitation, household, digest, created, expiry);
    invitations.insert(expired, household, otherDigest, created, expiry);
    transactions.executeWithoutResult(
        status -> {
          assertThat(invitations.lockHousehold(household)).isTrue();
          assertThat(invitations.lockInvitation(household, invitation)).isPresent();
          assertThat(invitations.revoke(invitation, created.plusSeconds(1))).isTrue();
          assertThat(invitations.consume(invitation, created.plusSeconds(2))).isFalse();
          assertThat(invitations.revoke(invitation, created.plusSeconds(2))).isFalse();
          assertThat(invitations.lockInvitation(household, expired)).isPresent();
          assertThat(invitations.consume(expired, expiry)).isFalse();
        });
    assertThat(
            jdbc.queryForObject(
                "SELECT consumed_at IS NULL AND revoked_at IS NOT NULL FROM household_invitations WHERE id = ?",
                Boolean.class,
                invitation))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT consumed_at IS NULL AND revoked_at IS NULL FROM household_invitations WHERE id = ?",
                Boolean.class,
                expired))
        .isTrue();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE household_invitations SET consumed_at = ?, revoked_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(created),
                    java.sql.Timestamp.from(created),
                    expired))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void consumptionRollsBackWithOtherChangesInTheSameTransaction() {
    Instant expiry = created.plus(24, ChronoUnit.HOURS);
    invitations.insert(invitation, household, digest, created, expiry);
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      assertThat(invitations.lockHousehold(household)).isTrue();
                      assertThat(invitations.lockInvitation(household, invitation)).isPresent();
                      assertThat(invitations.consume(invitation, created.plusSeconds(1))).isTrue();
                      throw new IllegalStateException("synthetic rollback");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT consumed_at IS NULL FROM household_invitations WHERE id = ?",
                Boolean.class,
                invitation))
        .isTrue();
  }
}
