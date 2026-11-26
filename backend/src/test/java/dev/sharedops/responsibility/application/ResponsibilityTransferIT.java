package dev.sharedops.responsibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.TestClockConfiguration;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import dev.sharedops.responsibility.domain.Responsibility;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class ResponsibilityTransferIT {
  @Autowired ResponsibilityService service;
  @Autowired JdbcTemplate jdbc;
  UUID home = UUID.randomUUID(), actor = UUID.randomUUID(), recipient = UUID.randomUUID();
  UUID actorMember = UUID.randomUUID(),
      recipientMember = UUID.randomUUID(),
      task = UUID.randomUUID();
  Responsibility initial;

  @BeforeEach
  void setup() {
    for (UUID account : new UUID[] {actor, recipient}) {
      jdbc.update(
          "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
          account,
          account + "@example.com");
    }
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin')",
        home);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?, ?, ?, true, 1), (?, ?, ?, true, 2)",
        actorMember,
        actor,
        home,
        recipientMember,
        recipient,
        home);
    var principal = new AccountPrincipal(actor, actor + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    initial = service.create(home, "Garden", null, actor);
    jdbc.update(
        "INSERT INTO tasks (id,household_id,responsibility_id,title,priority,status,version,deadline_kind) VALUES (?,?,?,'Task','NORMAL','OPEN',0,'NONE')",
        task,
        home,
        initial.id());
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.execute("DROP TRIGGER IF EXISTS ho12_forbid_tasks ON tasks");
    jdbc.execute("DROP FUNCTION IF EXISTS ho12_forbid_task_write()");
    jdbc.execute("DROP TRIGGER IF EXISTS ho12_fail_fact ON responsibility_owner_transfers");
    jdbc.execute("DROP FUNCTION IF EXISTS ho12_fail_fact_insert()");
    jdbc.update("DELETE FROM tasks WHERE id = ?", task);
    jdbc.update("DELETE FROM responsibility_owner_transfers WHERE household_id = ?", home);
    jdbc.update("DELETE FROM responsibilities WHERE household_id = ?", home);
    jdbc.update("DELETE FROM household_memberships WHERE household_id = ?", home);
    jdbc.update("DELETE FROM households WHERE id = ?", home);
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?)", actor, recipient);
  }

  @Test
  void transferAndEditLeaveLinkedTaskUntouchedAndRecordExactFact() {
    jdbc.execute(
        "CREATE FUNCTION ho12_forbid_task_write() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'task write forbidden'; END $$");
    jdbc.execute(
        "CREATE TRIGGER ho12_forbid_tasks BEFORE INSERT OR UPDATE OR DELETE ON tasks FOR EACH STATEMENT EXECUTE FUNCTION ho12_forbid_task_write()");
    Responsibility edited = service.edit(home, initial.id(), 0, "Flowers", "water");
    Responsibility transferred = service.transfer(home, initial.id(), 1, recipient);
    assertThat(transferred.version()).isEqualTo(2);
    assertThat(edited.title()).isEqualTo("Flowers");
    var fact =
        jdbc.queryForMap(
            "SELECT actor_account_id, occurred_at, previous_owner_membership_id, new_owner_membership_id FROM responsibility_owner_transfers WHERE responsibility_id = ?",
            initial.id());
    assertThat(fact.get("actor_account_id")).isEqualTo(actor);
    assertThat(((Timestamp) fact.get("occurred_at")).toInstant())
        .isEqualTo(Instant.parse("2026-10-25T12:00:00Z"));
    assertThat(fact.get("previous_owner_membership_id")).isEqualTo(actorMember);
    assertThat(fact.get("new_owner_membership_id")).isEqualTo(recipientMember);
    assertThat(
            jdbc.queryForObject(
                "SELECT responsibility_id FROM tasks WHERE id = ?", UUID.class, task))
        .isEqualTo(initial.id());
    assertThatThrownBy(() -> service.transfer(home, initial.id(), 0, UUID.randomUUID()))
        .isInstanceOf(ResponsibilityService.VersionConflict.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id = ?",
                Long.class,
                initial.id()))
        .isEqualTo(1);
  }

  @Test
  void failedFactInsertRollsBackOwnerAndVersionAndLeavesTask() {
    jdbc.execute(
        "CREATE FUNCTION ho12_fail_fact_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected fact failure'; END $$");
    jdbc.execute(
        "CREATE TRIGGER ho12_fail_fact BEFORE INSERT ON responsibility_owner_transfers FOR EACH ROW EXECUTE FUNCTION ho12_fail_fact_insert()");
    assertThatThrownBy(() -> service.transfer(home, initial.id(), 0, recipient))
        .isInstanceOf(RuntimeException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT owner_membership_id FROM responsibilities WHERE id = ?",
                UUID.class,
                initial.id()))
        .isEqualTo(actorMember);
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM responsibilities WHERE id = ?", Long.class, initial.id()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id = ?",
                Long.class,
                initial.id()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT responsibility_id FROM tasks WHERE id = ?", UUID.class, task))
        .isEqualTo(initial.id());
  }
}
