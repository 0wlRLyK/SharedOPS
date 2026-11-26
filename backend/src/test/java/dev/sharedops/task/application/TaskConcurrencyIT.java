package dev.sharedops.task.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.TestClockConfiguration;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class TaskConcurrencyIT {
  @Autowired TaskService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transaction;
  final UUID home = UUID.randomUUID(), actor = UUID.randomUUID(), partner = UUID.randomUUID();
  final UUID member = UUID.randomUUID(), partnerMember = UUID.randomUUID();
  final UUID responsibility = UUID.randomUUID();

  @BeforeEach
  void setup() {
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,'hash'),(?,?,'hash')",
        actor,
        actor + "@example.com",
        partner,
        partner + "@example.com");
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?,'Home','EUR','Europe/Berlin')",
        home);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1),(?,?,?,true,2)",
        member,
        actor,
        home,
        partnerMember,
        partner,
        home);
    jdbc.update(
        "INSERT INTO responsibilities (id,household_id,title,mode,status,owner_membership_id,version) VALUES (?,?,'R','INDIVIDUAL','ACTIVE',?,0)",
        responsibility,
        home,
        member);
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM task_edit_facts WHERE household_id = ?", home);
    jdbc.update("DELETE FROM tasks WHERE household_id = ?", home);
    jdbc.update("DELETE FROM responsibilities WHERE household_id = ?", home);
    jdbc.update("DELETE FROM household_memberships WHERE household_id = ?", home);
    jdbc.update("DELETE FROM households WHERE id = ?", home);
    jdbc.update("DELETE FROM accounts WHERE id IN (?,?)", actor, partner);
  }

  @Test
  void concurrentEditsHaveOneWinnerAndOneCommittedFact() throws Exception {
    authenticate();
    UUID id = service.createStandalone(home, "Original", null, null, null, null).task().id();
    SecurityContextHolder.clearContext();
    var ready = new CountDownLatch(2);
    var go = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> edit(id, "First", ready, go));
      var b = pool.submit(() -> edit(id, "Second", ready, go));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("success", "conflict");
    }
    assertThat(jdbc.queryForObject("SELECT version FROM tasks WHERE id=?", Long.class, id))
        .isEqualTo(1L);
    assertThat(jdbc.queryForObject("SELECT title FROM tasks WHERE id=?", String.class, id))
        .isIn("First", "Second");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM task_edit_facts WHERE task_id=? AND previous_version=0 AND new_version=1",
                Long.class,
                id))
        .isEqualTo(1L);
  }

  private String edit(UUID id, String title, CountDownLatch ready, CountDownLatch go) {
    authenticate();
    try {
      ready.countDown();
      await(go);
      try {
        service.edit(
            home,
            id,
            new TaskService.EditCommand(
                0,
                TaskService.Field.supplied(title),
                TaskService.Field.omitted(),
                TaskService.Field.omitted(),
                TaskService.Field.omitted(),
                TaskService.Field.omitted()));
        return "success";
      } catch (TaskVersionConflictException expected) {
        return "conflict";
      }
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void creationSerializesWithTransferAndPauseInBothOrders() throws Exception {
    for (String change : List.of("transfer", "pause")) {
      for (boolean creationFirst : new boolean[] {true, false}) {
        jdbc.update("DELETE FROM tasks WHERE household_id=?", home);
        jdbc.update(
            "UPDATE responsibilities SET status='ACTIVE', owner_membership_id=?, version=0 WHERE id=?",
            member,
            responsibility);
        Runnable mutate =
            () -> {
              if (change.equals("transfer")) {
                jdbc.update(
                    "UPDATE responsibilities SET owner_membership_id=?, version=version+1 WHERE id=?",
                    partnerMember,
                    responsibility);
              } else {
                jdbc.update(
                    "UPDATE responsibilities SET status='PAUSED', version=version+1 WHERE id=?",
                    responsibility);
              }
            };
        Outcome outcome = race(creationFirst, mutate, TaskService.AssignmentChoice.omitted());
        assertThat(outcome.mutation()).isTrue();
        assertThat(outcome.created()).isEqualTo(creationFirst || change.equals("transfer"));
        if (outcome.created()) {
          UUID expected = creationFirst || change.equals("pause") ? member : partnerMember;
          assertThat(
                  jdbc.queryForObject(
                      "SELECT assignee_membership_id FROM tasks WHERE household_id=?",
                      UUID.class,
                      home))
              .isEqualTo(expected);
        }
        assertThat(countTasks()).isEqualTo(outcome.created() ? 1L : 0L);
        assertThat(countFacts()).isZero();
      }
    }
  }

  @Test
  void creationSerializesWithMembershipChangesInBothOrders() throws Exception {
    for (String change : List.of("deactivate", "delete", "key")) {
      for (boolean creationFirst : new boolean[] {true, false}) {
        jdbc.update("DELETE FROM tasks WHERE household_id=?", home);
        jdbc.update(
            "DELETE FROM household_memberships WHERE account_id=? AND household_id=?",
            partner,
            home);
        jdbc.update(
            "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,2)",
            partnerMember,
            partner,
            home);
        UUID newId = UUID.randomUUID();
        Runnable mutate =
            () -> {
              switch (change) {
                case "deactivate" ->
                    jdbc.update(
                        "UPDATE household_memberships SET active=false, slot=NULL WHERE id=?",
                        partnerMember);
                case "delete" ->
                    jdbc.update("DELETE FROM household_memberships WHERE id=?", partnerMember);
                case "key" ->
                    jdbc.update(
                        "UPDATE household_memberships SET id=? WHERE id=?", newId, partnerMember);
                default -> throw new IllegalArgumentException(change);
              }
            };
        Outcome outcome =
            race(creationFirst, mutate, TaskService.AssignmentChoice.selected(partner));
        if (creationFirst) {
          assertThat(outcome.created()).isTrue();
          assertThat(outcome.mutation()).isEqualTo(change.equals("deactivate"));
          assertThat(
                  jdbc.queryForObject(
                      "SELECT assignee_membership_id FROM tasks WHERE household_id=?",
                      UUID.class,
                      home))
              .isEqualTo(partnerMember);
        } else {
          assertThat(outcome.mutation()).isTrue();
          assertThat(outcome.created()).isEqualTo(change.equals("key"));
          if (outcome.created()) {
            assertThat(
                    jdbc.queryForObject(
                        "SELECT assignee_membership_id FROM tasks WHERE household_id=?",
                        UUID.class,
                        home))
                .isEqualTo(newId);
          }
        }
        assertThat(countTasks()).isEqualTo(outcome.created() ? 1L : 0L);
        assertThat(countFacts()).isZero();
      }
    }
  }

  private record Outcome(boolean created, boolean mutation) {}

  private Outcome race(
      boolean creationFirst, Runnable mutation, TaskService.AssignmentChoice choice)
      throws Exception {
    var firstDone = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var secondStarted = new CountDownLatch(1);
    var probeStarted = new CountDownLatch(1);
    var firstPid = new AtomicInteger();
    var secondPid = new AtomicInteger();
    var probePid = new AtomicInteger();
    Callable<Boolean> create =
        () -> {
          authenticate();
          try {
            service.createLinked(home, responsibility, "Race", null, null, null, choice);
            return true;
          } finally {
            SecurityContextHolder.clearContext();
          }
        };
    Callable<Boolean> mutate =
        () -> {
          mutation.run();
          return true;
        };
    try (var pool = Executors.newFixedThreadPool(3)) {
      var first =
          pool.submit(
              () ->
                  transaction.execute(
                      status -> {
                        firstPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        // The operation itself must acquire the household lock. Keep its
                        // transaction open after it returns so the competing operation can
                        // demonstrate a real wait on that lock.
                        boolean result = call(creationFirst ? create : mutate);
                        firstDone.countDown();
                        await(release);
                        return result;
                      }));
      assertThat(firstDone.await(10, TimeUnit.SECONDS)).isTrue();
      // Prove the actual first operation, rather than the harness, holds the household
      // row. Cancel this independent probe after observing its wait so it cannot acquire
      // the lock ahead of the real competitor.
      var probe =
          pool.submit(
              () -> {
                try {
                  transaction.execute(
                      status -> {
                        probePid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        probeStarted.countDown();
                        jdbc.queryForObject(
                            "SELECT id FROM households WHERE id=? FOR UPDATE", UUID.class, home);
                        return null;
                      });
                  return true;
                } catch (DataAccessException expected) {
                  return false;
                }
              });
      assertThat(probeStarted.await(10, TimeUnit.SECONDS)).isTrue();
      awaitBlockedBy(firstPid.get(), probePid.get());
      assertThat(jdbc.queryForObject("SELECT pg_cancel_backend(?)", Boolean.class, probePid.get()))
          .isTrue();
      assertThat(probe.get(10, TimeUnit.SECONDS)).isFalse();
      var second =
          pool.submit(
              () -> {
                try {
                  return transaction.execute(
                      status -> {
                        secondPid.set(
                            jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        secondStarted.countDown();
                        return call(creationFirst ? mutate : create);
                      });
                } catch (TaskService.InvalidState
                    | TaskService.InvalidAssignee
                    | DataAccessException expected) {
                  // Classify only after the transactional proxy and outer transaction
                  // have rolled back; catching inside would cause an unexpected rollback.
                  return false;
                }
              });
      try {
        assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
        awaitBlockedBy(firstPid.get(), secondPid.get());
        assertThat(second.isDone()).isFalse();
      } finally {
        release.countDown();
      }
      boolean firstResult = first.get(20, TimeUnit.SECONDS);
      boolean secondResult = second.get(20, TimeUnit.SECONDS);
      return creationFirst
          ? new Outcome(firstResult, secondResult)
          : new Outcome(secondResult, firstResult);
    }
  }

  /** Observe PostgreSQL's actual lock wait rather than merely a scheduled Java thread. */
  private void awaitBlockedBy(int holderPid, int waiterPid) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    boolean waiting = false;
    while (System.nanoTime() < deadline) {
      waiting =
          Boolean.TRUE.equals(
              jdbc.queryForObject(
                  """
                  SELECT EXISTS (
                    SELECT 1 FROM pg_stat_activity activity
                    WHERE activity.pid = ? AND activity.wait_event_type = 'Lock'
                      AND ? = ANY(pg_blocking_pids(activity.pid))
                  )
                  """,
                  Boolean.class,
                  waiterPid,
                  holderPid));
      if (waiting) {
        break;
      }
      Thread.sleep(25);
    }
    assertThat(waiting)
        .as("backend %s waits on a lock held by backend %s", waiterPid, holderPid)
        .isTrue();
  }

  private long countTasks() {
    return jdbc.queryForObject("SELECT count(*) FROM tasks WHERE household_id=?", Long.class, home);
  }

  private long countFacts() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM task_edit_facts WHERE household_id=?", Long.class, home);
  }

  private static boolean call(Callable<Boolean> action) {
    try {
      return action.call();
    } catch (RuntimeException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private void authenticate() {
    var principal = new AccountPrincipal(actor, actor + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("barrier timed out");
      }
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(failure);
    }
  }
}
