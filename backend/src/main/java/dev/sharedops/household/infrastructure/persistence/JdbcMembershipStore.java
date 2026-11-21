package dev.sharedops.household.infrastructure.persistence;

import dev.sharedops.household.application.MembershipStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMembershipStore implements MembershipStore {
  private final JdbcTemplate jdbc;

  public JdbcMembershipStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<UUID> findActiveHouseholdId(UUID accountId) {
    List<UUID> rows =
        jdbc.query(
            "SELECT household_id FROM household_memberships WHERE account_id = ? AND active",
            (rs, row) -> rs.getObject(1, UUID.class),
            accountId);
    return rows.stream().findFirst();
  }

  @Override
  public boolean isActiveMember(UUID accountId, UUID householdId) {
    Boolean result =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM household_memberships WHERE account_id = ? AND household_id = ? AND active)",
            Boolean.class,
            accountId,
            householdId);
    return Boolean.TRUE.equals(result);
  }

  @Override
  public void insertActive(UUID accountId, UUID householdId) {
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active) VALUES (?, ?, ?, true)",
        UUID.randomUUID(),
        accountId,
        householdId);
  }
}
