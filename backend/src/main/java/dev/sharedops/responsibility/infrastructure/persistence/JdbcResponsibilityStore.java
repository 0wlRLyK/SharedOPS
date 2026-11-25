package dev.sharedops.responsibility.infrastructure.persistence;

import dev.sharedops.responsibility.application.ResponsibilityStore;
import dev.sharedops.responsibility.domain.Responsibility;
import dev.sharedops.responsibility.domain.ResponsibilityMode;
import dev.sharedops.responsibility.domain.ResponsibilityStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL implementation; authorization and transaction ownership belong to the application. */
@Repository
public class JdbcResponsibilityStore implements ResponsibilityStore {
  private static final String COLUMNS =
      "id, household_id, title, description, mode, status, owner_membership_id, version";
  private final JdbcTemplate jdbc;

  public JdbcResponsibilityStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Responsibility r) {
    jdbc.update(
        "INSERT INTO responsibilities (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        r.id(),
        r.householdId(),
        r.title(),
        r.description(),
        r.mode().name(),
        r.status().name(),
        r.ownerMembershipId(),
        r.version());
  }

  @Override
  public Optional<Responsibility> find(UUID householdId, UUID responsibilityId) {
    return findInternal(householdId, responsibilityId, false);
  }

  @Override
  public Optional<Responsibility> lock(UUID householdId, UUID responsibilityId) {
    return findInternal(householdId, responsibilityId, true);
  }

  private Optional<Responsibility> findInternal(
      UUID householdId, UUID responsibilityId, boolean lock) {
    return jdbc
        .query(
            "SELECT "
                + COLUMNS
                + " FROM responsibilities WHERE household_id = ? AND id = ?"
                + (lock ? " FOR UPDATE" : ""),
            (rs, row) -> map(rs),
            householdId,
            responsibilityId)
        .stream()
        .findFirst();
  }

  @Override
  public List<Responsibility> list(UUID householdId, boolean archived) {
    String states = archived ? "status = 'ARCHIVED'" : "status IN ('ACTIVE', 'PAUSED')";
    return jdbc.query(
        "SELECT "
            + COLUMNS
            + " FROM responsibilities WHERE household_id = ? AND "
            + states
            + " ORDER BY id",
        (rs, row) -> map(rs),
        householdId);
  }

  @Override
  public boolean update(Responsibility r, long expectedVersion) {
    if (expectedVersion < 0
        || expectedVersion == Long.MAX_VALUE
        || r.version() != expectedVersion + 1) {
      throw new IllegalArgumentException("A changed aggregate must advance exactly one version");
    }
    return jdbc.update(
            "UPDATE responsibilities SET title = ?, description = ?, mode = ?, status = ?, "
                + "owner_membership_id = ?, version = ? WHERE household_id = ? AND id = ? AND version = ?",
            r.title(),
            r.description(),
            r.mode().name(),
            r.status().name(),
            r.ownerMembershipId(),
            r.version(),
            r.householdId(),
            r.id(),
            expectedVersion)
        == 1;
  }

  @Override
  public void insertTransferFact(TransferFact fact) {
    jdbc.update(
        "INSERT INTO responsibility_owner_transfers "
            + "(id, household_id, responsibility_id, actor_account_id, occurred_at, "
            + "previous_owner_membership_id, new_owner_membership_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
        fact.id(),
        fact.householdId(),
        fact.responsibilityId(),
        fact.actorAccountId(),
        Timestamp.from(fact.occurredAt()),
        fact.previousOwnerMembershipId(),
        fact.newOwnerMembershipId());
  }

  private static Responsibility map(ResultSet rs) throws SQLException {
    return Responsibility.rehydrate(
        rs.getObject("id", UUID.class),
        rs.getObject("household_id", UUID.class),
        rs.getString("title"),
        rs.getString("description"),
        ResponsibilityMode.valueOf(rs.getString("mode")),
        ResponsibilityStatus.valueOf(rs.getString("status")),
        rs.getObject("owner_membership_id", UUID.class),
        rs.getLong("version"));
  }
}
