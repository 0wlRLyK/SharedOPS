package dev.sharedops.household.infrastructure.persistence;

import dev.sharedops.household.application.HouseholdDraft;
import dev.sharedops.household.application.HouseholdSnapshot;
import dev.sharedops.household.application.HouseholdStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HouseholdPersistenceAdapter implements HouseholdStore {
  private final JdbcTemplate jdbc;

  public HouseholdPersistenceAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public HouseholdSnapshot insert(HouseholdDraft draft) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO households (id,name,icon,currency,timezone) VALUES (?, ?, ?, ?, ?)",
        id,
        draft.name(),
        draft.icon(),
        draft.currency(),
        draft.timezone());
    return new HouseholdSnapshot(
        id, draft.name(), draft.icon(), draft.currency(), draft.timezone());
  }

  @Override
  public Optional<HouseholdSnapshot> findById(UUID householdId) {
    List<HouseholdSnapshot> rows =
        jdbc.query(
            "SELECT id,name,icon,currency,timezone FROM households WHERE id = ?",
            (rs, row) ->
                new HouseholdSnapshot(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("icon"),
                    rs.getString("currency"),
                    rs.getString("timezone")),
            householdId);
    return rows.stream().findFirst();
  }
}
