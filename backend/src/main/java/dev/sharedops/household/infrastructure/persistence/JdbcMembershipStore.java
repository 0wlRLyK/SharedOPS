package dev.sharedops.household.infrastructure.persistence;

import dev.sharedops.household.application.MembershipStore;
import dev.sharedops.household.application.ResponsibilityMembershipStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcMembershipStore implements MembershipStore, ResponsibilityMembershipStore {
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
  public Optional<UUID> findActiveMembershipId(UUID accountId, UUID householdId) {
    List<UUID> rows =
        jdbc.query(
            "SELECT id FROM household_memberships WHERE account_id = ? AND household_id = ? AND active",
            (rs, row) -> rs.getObject(1, UUID.class),
            accountId,
            householdId);
    return rows.stream().findFirst();
  }

  @Override
  public Optional<UUID> findAccountId(UUID membershipId, UUID householdId) {
    return jdbc
        .query(
            "SELECT account_id FROM household_memberships WHERE id = ? AND household_id = ?",
            (rs, row) -> rs.getObject(1, UUID.class),
            membershipId,
            householdId)
        .stream()
        .findFirst();
  }

  @Override
  public boolean isActiveMembership(UUID membershipId, UUID householdId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM household_memberships WHERE id = ? AND household_id = ? AND active)",
            Boolean.class,
            membershipId,
            householdId));
  }

  @Override
  @Transactional
  public void insertActive(UUID accountId, UUID householdId) {
    // The household row is the shared serialization point for every membership writer.
    jdbc.queryForObject(
        "SELECT id FROM households WHERE id = ? FOR UPDATE", UUID.class, householdId);
    List<Integer> available =
        jdbc.queryForList(
            "SELECT candidate.slot FROM (VALUES (1), (2)) AS candidate(slot) "
                + "WHERE NOT EXISTS (SELECT 1 FROM household_memberships m "
                + "WHERE m.household_id = ? AND m.active AND m.slot = candidate.slot) "
                + "ORDER BY candidate.slot LIMIT 1",
            Integer.class,
            householdId);
    if (available.isEmpty()) {
      throw new IllegalStateException("Household has no available membership slot");
    }
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?, ?, ?, true, ?)",
        UUID.randomUUID(),
        accountId,
        householdId,
        available.getFirst());
  }
}
