package dev.sharedops.household.infrastructure.persistence;

import dev.sharedops.household.application.CurrentContext;
import dev.sharedops.household.application.CurrentContextStore;
import dev.sharedops.household.application.HouseholdSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCurrentContextStore implements CurrentContextStore {
  private final JdbcTemplate jdbc;

  public JdbcCurrentContextStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<CurrentContext> findForAccount(UUID accountId) {
    return jdbc
        .query(
            """
            SELECT a.id AS account_id, a.email, h.id AS household_id,
                   h.name, h.icon, h.currency, h.timezone
              FROM accounts a
              LEFT JOIN household_memberships m ON m.account_id = a.id AND m.active
              LEFT JOIN households h ON h.id = m.household_id
             WHERE a.id = ?
            """,
            (rs, row) -> {
              UUID householdId = rs.getObject("household_id", UUID.class);
              HouseholdSnapshot household =
                  householdId == null
                      ? null
                      : new HouseholdSnapshot(
                          householdId,
                          rs.getString("name"),
                          rs.getString("icon"),
                          rs.getString("currency"),
                          rs.getString("timezone"));
              return new CurrentContext(
                  rs.getObject("account_id", UUID.class), rs.getString("email"), household);
            },
            accountId)
        .stream()
        .findFirst();
  }
}
