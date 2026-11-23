package dev.sharedops.household.infrastructure.persistence;

import dev.sharedops.household.application.InvitationStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC invitation operations, called within the application's transaction. */
@Repository
public class JdbcInvitationStore implements InvitationStore {
  private final JdbcTemplate jdbc;

  public JdbcInvitationStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Locator> findByDigest(byte[] digest) {
    List<Locator> rows =
        jdbc.query(
            "SELECT id, household_id FROM household_invitations WHERE token_hash = ?",
            (rs, row) ->
                new Locator(
                    rs.getObject("id", UUID.class), rs.getObject("household_id", UUID.class)),
            digest);
    return rows.stream().findFirst();
  }

  @Override
  public boolean lockHousehold(UUID householdId) {
    return !jdbc.query(
            "SELECT id FROM households WHERE id = ? FOR UPDATE",
            (rs, row) -> rs.getObject(1, UUID.class),
            householdId)
        .isEmpty();
  }

  @Override
  public Optional<State> lockInvitation(UUID householdId, UUID invitationId) {
    List<State> rows =
        jdbc.query(
            "SELECT id, household_id, expires_at, consumed_at, revoked_at FROM household_invitations "
                + "WHERE household_id = ? AND id = ? FOR UPDATE",
            (rs, row) ->
                new State(
                    rs.getObject("id", UUID.class),
                    rs.getObject("household_id", UUID.class),
                    rs.getTimestamp("expires_at").toInstant(),
                    instant(rs.getTimestamp("consumed_at")),
                    instant(rs.getTimestamp("revoked_at"))),
            householdId,
            invitationId);
    return rows.stream().findFirst();
  }

  @Override
  public int activeMemberCount(UUID householdId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM household_memberships WHERE household_id = ? AND active",
            Integer.class,
            householdId);
    return count == null ? 0 : count;
  }

  @Override
  public void insert(
      UUID id, UUID householdId, byte[] digest, Instant createdAt, Instant expiresAt) {
    jdbc.update(
        "INSERT INTO household_invitations (id, household_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
        id,
        householdId,
        digest,
        Timestamp.from(createdAt),
        Timestamp.from(expiresAt));
  }

  @Override
  public boolean consume(UUID invitationId, Instant consumedAt) {
    // PostgreSQL stores microseconds. Truncating avoids JDBC rounding a valid instant
    // just before the deadline up to the deadline for the conditional update.
    Instant persistedTime = consumedAt.truncatedTo(ChronoUnit.MICROS);
    return jdbc.update(
            "UPDATE household_invitations SET consumed_at = ? "
                + "WHERE id = ? AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > ?",
            Timestamp.from(persistedTime),
            invitationId,
            Timestamp.from(persistedTime))
        == 1;
  }

  @Override
  public boolean revoke(UUID invitationId, Instant revokedAt) {
    return jdbc.update(
            "UPDATE household_invitations SET revoked_at = ? "
                + "WHERE id = ? AND consumed_at IS NULL AND revoked_at IS NULL",
            Timestamp.from(revokedAt),
            invitationId)
        == 1;
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
