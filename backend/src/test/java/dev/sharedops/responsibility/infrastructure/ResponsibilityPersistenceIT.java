package dev.sharedops.responsibility.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.responsibility.application.ResponsibilityStore;
import dev.sharedops.responsibility.application.ResponsibilityStore.TransferFact;
import dev.sharedops.responsibility.domain.Responsibility;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ResponsibilityPersistenceIT {
  @Autowired ResponsibilityStore store;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transactions;

  private final UUID household = UUID.randomUUID();
  private final UUID foreignHousehold = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID other = UUID.randomUUID();
  private final UUID owner = UUID.randomUUID();
  private final UUID replacement = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();
  private final Instant at = Instant.parse("2026-02-03T04:05:06Z");

  @BeforeEach
  void setUp() {
    jdbc.update(
        "INSERT INTO households (id, name, currency, timezone) VALUES (?, 'A', 'EUR', 'Europe/Berlin'), (?, 'B', 'EUR', 'Europe/Berlin')",
        household,
        foreignHousehold);
    jdbc.update(
        "INSERT INTO accounts (id, email, password_hash) VALUES (?, ?, 'hash'), (?, ?, 'hash')",
        actor,
        actor + "@example.test",
        other,
        other + "@example.test");
    jdbc.update(
        "INSERT INTO household_memberships (id, account_id, household_id, active, slot) VALUES (?, ?, ?, true, 1), (?, ?, ?, true, 2)",
        owner,
        actor,
        household,
        replacement,
        other,
        household);
  }

  @AfterEach
  void tearDown() {
    jdbc.update("DELETE FROM responsibility_owner_transfers WHERE household_id = ?", household);
    jdbc.update("DELETE FROM responsibilities WHERE household_id = ?", household);
    jdbc.update(
        "DELETE FROM household_memberships WHERE household_id IN (?, ?)",
        household,
        foreignHousehold);
    jdbc.update("DELETE FROM households WHERE id IN (?, ?)", household, foreignHousehold);
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?)", actor, other);
  }

  private Responsibility initial() {
    return Responsibility.create(id, household, "Water plants", null, owner);
  }

  @Test
  void scopedReadsSeparateCurrentAndArchivedAndOrderById() {
    Responsibility first = initial();
    UUID secondId = UUID.randomUUID();
    Responsibility second = Responsibility.create(secondId, household, "Bins", "", owner);
    store.insert(first);
    store.insert(second);
    assertThat(store.find(foreignHousehold, id)).isEmpty();
    assertThat(store.lock(foreignHousehold, id)).isEmpty();
    assertThat(store.find(household, id)).contains(first);
    assertThat(store.list(foreignHousehold, false)).isEmpty();
    assertThat(store.list(household, false))
        .extracting(Responsibility::id)
        .containsExactlyElementsOf(
            java.util.stream.Stream.of(id, secondId)
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .toList());
    assertThat(store.update(first.pause(), 0)).isTrue();
    assertThat(store.update(second.archive(), 0)).isTrue();
    assertThat(store.list(household, false)).extracting(Responsibility::id).containsExactly(id);
    assertThat(store.list(household, true))
        .extracting(Responsibility::id)
        .containsExactly(secondId);
    assertThat(store.find(household, secondId))
        .get()
        .extracting(Responsibility::description)
        .isEqualTo("");
  }

  @Test
  void expectedVersionAndHouseholdGuardPreventOverwritingNewerData() {
    Responsibility first = initial();
    store.insert(first);
    Responsibility changed = first.edit("Updated", "note");
    assertThat(store.update(changed, 0)).isTrue();
    assertThat(store.update(first.edit("Stale", null), 0)).isFalse();
    Responsibility foreign =
        Responsibility.rehydrate(
            id, foreignHousehold, "Other", null, changed.mode(), changed.status(), owner, 2);
    assertThat(store.update(foreign, 1)).isFalse();
    assertThat(store.find(household, id)).contains(changed);
    assertThatThrownBy(() -> store.update(changed, 1))
        .isInstanceOf(InvalidDataAccessApiUsageException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("A changed aggregate must advance exactly one version");
    assertThat(store.find(household, id)).contains(changed);
  }

  @Test
  void transferFactCommitsAtomicallyAndFailureRollsBackOwnerChange() {
    Responsibility first = initial();
    store.insert(first);
    UUID factId = UUID.randomUUID();
    TransferFact fact = new TransferFact(factId, household, id, actor, at, owner, replacement);
    transactions.executeWithoutResult(
        status -> {
          assertThat(store.lock(household, id)).contains(first);
          assertThat(store.update(first.transfer(replacement), 0)).isTrue();
          store.insertTransferFact(fact);
        });
    assertThat(store.find(household, id))
        .get()
        .extracting(Responsibility::ownerMembershipId)
        .isEqualTo(replacement);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE id = ? AND actor_account_id = ? AND previous_owner_membership_id = ? AND new_owner_membership_id = ? AND occurred_at = ?",
                Integer.class,
                factId,
                actor,
                owner,
                replacement,
                java.sql.Timestamp.from(at)))
        .isEqualTo(1);
    Responsibility current = store.find(household, id).orElseThrow();
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      assertThat(store.update(current.transfer(owner), 1)).isTrue();
                      store.insertTransferFact(
                          new TransferFact(factId, household, id, actor, at, replacement, owner));
                    }))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(store.find(household, id)).contains(current);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id = ?",
                Integer.class,
                id))
        .isEqualTo(1);
  }
}
