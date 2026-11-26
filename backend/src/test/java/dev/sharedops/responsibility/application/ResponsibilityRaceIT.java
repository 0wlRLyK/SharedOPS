package dev.sharedops.responsibility.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.TestClockConfiguration;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class ResponsibilityRaceIT {
  @Autowired ResponsibilityService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired org.springframework.transaction.support.TransactionTemplate transaction;
  final UUID home = UUID.randomUUID(), actor = UUID.randomUUID(), member = UUID.randomUUID();
  final UUID recipient = UUID.randomUUID(), recipientMember = UUID.randomUUID();
  UUID responsibility;

  @BeforeEach
  void setup() {
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
        actor,
        actor + "@example.com");
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin')",
        home);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?, ?, ?, true, 1)",
        member,
        actor,
        home);
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
        recipient,
        recipient + "@example.com");
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?, ?, ?, true, 2)",
        recipientMember,
        recipient,
        home);
    authenticate();
    responsibility = service.create(home, "Original", null, actor).id();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM responsibility_owner_transfers WHERE household_id = ?", home);
    jdbc.update("DELETE FROM responsibilities WHERE household_id = ?", home);
    jdbc.update("DELETE FROM household_memberships WHERE household_id = ?", home);
    jdbc.update("DELETE FROM households WHERE id = ?", home);
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?)", actor, recipient);
  }

  private void authenticate() {
    var principal = new AccountPrincipal(actor, actor + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }

  @Test
  void simultaneousEditsAtSameVersionCommitExactlyOneChange() throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<String> first = () -> editAfterBarrier("First", ready, start);
      java.util.concurrent.Callable<String> second = () -> editAfterBarrier("Second", ready, start);
      var a = pool.submit(first);
      var b = pool.submit(second);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(java.util.List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("success", "conflict");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM responsibilities WHERE id = ?", Long.class, responsibility))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT title FROM responsibilities WHERE id = ?", String.class, responsibility))
        .isIn("First", "Second");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id = ?",
                Long.class,
                responsibility))
        .isZero();
  }

  private String editAfterBarrier(String title, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    authenticate();
    try {
      ready.countDown();
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("barrier timed out");
      }
      try {
        service.edit(home, responsibility, 0, title, null);
        return "success";
      } catch (ResponsibilityService.VersionConflict expected) {
        return "conflict";
      }
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void concurrentTransferAndEditAtSameVersionHaveOneWinner() throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var transfer =
          pool.submit(
              () -> {
                authenticate();
                try {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("barbarrier timed out");
                  }
                  try {
                    service.transfer(home, responsibility, 0, recipient);
                    return "success";
                  } catch (ResponsibilityService.VersionConflict expected) {
                    return "conflict";
                  }
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      var edit = pool.submit(() -> editAfterBarrier("Edited", ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              java.util.List.of(transfer.get(20, TimeUnit.SECONDS), edit.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("success", "conflict");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM responsibilities WHERE id = ?", Long.class, responsibility))
        .isEqualTo(1L);
    var owner =
        jdbc.queryForObject(
            "SELECT owner_membership_id FROM responsibilities WHERE id = ?",
            UUID.class,
            responsibility);
    var facts =
        jdbc.queryForObject(
            "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id = ?",
            Long.class,
            responsibility);
    assertThat(facts).isEqualTo(owner.equals(recipientMember) ? 1L : 0L);
  }

  @Test
  void sameOwnerNoopCanSucceedWithoutAdvancingVersionBeforeConcurrentEdit() throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var noop =
          pool.submit(
              () -> {
                authenticate();
                try {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("barrier timed out");
                  }
                  try {
                    service.transfer(home, responsibility, 0, actor);
                    return "success";
                  } catch (ResponsibilityService.VersionConflict expected) {
                    return "conflict";
                  }
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      var edit = pool.submit(() -> editAfterBarrier("Edited", ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(edit.get(20, TimeUnit.SECONDS)).isEqualTo("success");
      assertThat(noop.get(20, TimeUnit.SECONDS)).isIn("success", "conflict");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM responsibilities WHERE id = ?", Long.class, responsibility))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id = ?",
                Long.class,
                responsibility))
        .isZero();
  }

  @Test
  void directSqlCannotDeactivateTheCurrentOwner() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE household_memberships SET active = false, slot = null WHERE id = ?",
                    member))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT active FROM household_memberships WHERE id = ?", Boolean.class, member))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT owner_membership_id FROM responsibilities WHERE id = ?",
                UUID.class,
                responsibility))
        .isEqualTo(member);
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM responsibilities WHERE id = ?", Long.class, responsibility))
        .isZero();
  }

  @Test
  void ownershipInsertWinsAgainstConcurrentMemberDeactivation() throws Exception {
    jdbc.update("UPDATE responsibilities SET status = 'ARCHIVED' WHERE id = ?", responsibility);
    var ownsHousehold = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var deactivationStarted = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var creator =
          pool.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        jdbc.queryForObject(
                            "SELECT id FROM households WHERE id = ? FOR UPDATE", UUID.class, home);
                        jdbc.update(
                            "UPDATE responsibilities SET status = 'ACTIVE', version = version + 1 WHERE id = ?",
                            responsibility);
                        ownsHousehold.countDown();
                        await(release);
                      }));
      assertThat(ownsHousehold.await(10, TimeUnit.SECONDS)).isTrue();
      var deactivator =
          pool.submit(
              () -> {
                deactivationStarted.countDown();
                try {
                  jdbc.update(
                      "UPDATE household_memberships SET active = false, slot = null WHERE id = ?",
                      member);
                  return true;
                } catch (org.springframework.dao.DataAccessException rejected) {
                  return false;
                }
              });
      assertThat(deactivationStarted.await(10, TimeUnit.SECONDS)).isTrue();
      release.countDown();
      creator.get(20, TimeUnit.SECONDS);
      assertThat(deactivator.get(20, TimeUnit.SECONDS)).isFalse();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT active FROM household_memberships WHERE id = ?", Boolean.class, member))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM responsibilities WHERE id = ?", String.class, responsibility))
        .isEqualTo("ACTIVE");
  }

  @Test
  void deactivationWinsBeforeConcurrentRestore() throws Exception {
    jdbc.update("UPDATE responsibilities SET status = 'ARCHIVED' WHERE id = ?", responsibility);
    var changed = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var restoreStarted = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var deactivator =
          pool.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        jdbc.update(
                            "UPDATE household_memberships SET active = false, slot = null WHERE id = ?",
                            member);
                        changed.countDown();
                        await(release);
                      }));
      assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
      var restorer =
          pool.submit(
              () -> {
                restoreStarted.countDown();
                try {
                  jdbc.update(
                      "UPDATE responsibilities SET status = 'ACTIVE', version = version + 1 WHERE id = ?",
                      responsibility);
                  return true;
                } catch (org.springframework.dao.DataAccessException rejected) {
                  return false;
                }
              });
      assertThat(restoreStarted.await(10, TimeUnit.SECONDS)).isTrue();
      release.countDown();
      deactivator.get(20, TimeUnit.SECONDS);
      assertThat(restorer.get(20, TimeUnit.SECONDS)).isFalse();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT active FROM household_memberships WHERE id = ?", Boolean.class, member))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM responsibilities WHERE id = ?", String.class, responsibility))
        .isEqualTo("ARCHIVED");
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM responsibilities WHERE id = ?", Long.class, responsibility))
        .isZero();
  }

  @Test
  void createAndDeactivateRespectBothLockOrders() throws Exception {
    raceOwnerChangeAndDeactivation(true, true);
    jdbc.update(
        "DELETE FROM responsibilities WHERE id <> ? AND household_id = ?", responsibility, home);
    raceOwnerChangeAndDeactivation(false, true);
  }

  @Test
  void transferAndDeactivateRespectBothLockOrders() throws Exception {
    raceOwnerChangeAndDeactivation(true, false);
    jdbc.update(
        "UPDATE responsibilities SET owner_membership_id = ?, version = 0 WHERE id = ?",
        member,
        responsibility);
    raceOwnerChangeAndDeactivation(false, false);
  }

  private void raceOwnerChangeAndDeactivation(boolean ownerFirst, boolean create) throws Exception {
    jdbc.update(
        "UPDATE household_memberships SET active = true, slot = 2 WHERE id = ?", recipientMember);
    UUID candidate = create ? UUID.randomUUID() : responsibility;
    var changed = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var otherStarted = new CountDownLatch(1);
    Runnable ownerChange =
        () -> {
          if (create) {
            jdbc.update(
                "INSERT INTO responsibilities (id, household_id, title, mode, status, owner_membership_id, version) VALUES (?, ?, 'Race', 'INDIVIDUAL', 'ACTIVE', ?, 0)",
                candidate,
                home,
                recipientMember);
          } else {
            jdbc.update(
                "UPDATE responsibilities SET owner_membership_id = ?, version = 1 WHERE id = ?",
                recipientMember,
                candidate);
          }
        };
    Runnable deactivate =
        () ->
            jdbc.update(
                "UPDATE household_memberships SET active = false, slot = null WHERE id = ?",
                recipientMember);
    Runnable first = ownerFirst ? ownerChange : deactivate;
    Runnable second = ownerFirst ? deactivate : ownerChange;
    try (var pool = Executors.newFixedThreadPool(2)) {
      var winner =
          pool.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        first.run();
                        changed.countDown();
                        await(release);
                      }));
      assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
      var loser =
          pool.submit(
              () -> {
                otherStarted.countDown();
                try {
                  second.run();
                  return true;
                } catch (org.springframework.dao.DataAccessException rejected) {
                  return false;
                }
              });
      assertThat(otherStarted.await(10, TimeUnit.SECONDS)).isTrue();
      release.countDown();
      winner.get(20, TimeUnit.SECONDS);
      assertThat(loser.get(20, TimeUnit.SECONDS)).isFalse();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT active FROM household_memberships WHERE id = ?",
                Boolean.class,
                recipientMember))
        .isEqualTo(ownerFirst);
    if (create) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM responsibilities WHERE id = ?", Long.class, candidate))
          .isEqualTo(ownerFirst ? 1L : 0L);
    } else {
      assertThat(
              jdbc.queryForObject(
                  "SELECT owner_membership_id FROM responsibilities WHERE id = ?",
                  UUID.class,
                  responsibility))
          .isEqualTo(ownerFirst ? recipientMember : member);
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM responsibilities WHERE id = ?", Long.class, responsibility))
          .isEqualTo(ownerFirst ? 1L : 0L);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE household_id = ?",
                Long.class,
                home))
        .isZero();
  }

  @Test
  void serviceOwnershipChangesSerializeWithDeactivationInBothOrders() throws Exception {
    for (String operation : java.util.List.of("create", "transfer", "restore")) {
      for (boolean serviceFirst : new boolean[] {true, false}) {
        serviceRace(operation, serviceFirst);
      }
    }
  }

  private void serviceRace(String operation, boolean serviceFirst) throws Exception {
    UUID subject = UUID.randomUUID();
    UUID linked = UUID.randomUUID();
    jdbc.update(
        "UPDATE household_memberships SET active = true, slot = 2 WHERE id = ?", recipientMember);
    if (!operation.equals("create")) {
      jdbc.update(
          "INSERT INTO responsibilities (id,household_id,title,mode,status,owner_membership_id,version) VALUES (?,?,'Subject','INDIVIDUAL',?,?,0)",
          subject,
          home,
          operation.equals("restore") ? "ARCHIVED" : "ACTIVE",
          operation.equals("restore") ? recipientMember : member);
      jdbc.update(
          "INSERT INTO tasks (id,household_id,responsibility_id,title,priority,status,version,deadline_kind) VALUES (?,?,?,'Task','NORMAL','OPEN',0,'NONE')",
          linked,
          home,
          subject);
    }
    var firstDone = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var secondStarted = new CountDownLatch(1);
    java.util.concurrent.Callable<Boolean> operationCall =
        () -> {
          authenticate();
          try {
            if (operation.equals("create")) {
              service.create(home, "Race service", null, recipient);
            } else if (operation.equals("transfer")) {
              service.transfer(home, subject, 0, recipient);
            } else {
              service.restore(home, subject, 0, null);
            }
            return true;
          } catch (ResponsibilityService.InvalidOwner
              | org.springframework.dao.DataAccessException rejected) {
            return false;
          } finally {
            SecurityContextHolder.clearContext();
          }
        };
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () ->
                  transaction.execute(
                      status -> {
                        if (serviceFirst) {
                          try {
                            assertThat(operationCall.call()).isTrue();
                          } catch (Exception e) {
                            throw new IllegalStateException(e);
                          }
                        } else {
                          jdbc.update(
                              "UPDATE household_memberships SET active = false, slot = null WHERE id = ?",
                              recipientMember);
                        }
                        firstDone.countDown();
                        await(release);
                        return true;
                      }));
      assertThat(firstDone.await(10, TimeUnit.SECONDS)).isTrue();
      var second =
          pool.submit(
              () -> {
                secondStarted.countDown();
                if (!serviceFirst) {
                  return operationCall.call();
                }
                try {
                  jdbc.update(
                      "UPDATE household_memberships SET active = false, slot = null WHERE id = ?",
                      recipientMember);
                  return true;
                } catch (org.springframework.dao.DataAccessException rejected) {
                  return false;
                }
              });
      assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
      release.countDown();
      assertThat(first.get(20, TimeUnit.SECONDS)).isTrue();
      assertThat(second.get(20, TimeUnit.SECONDS)).isFalse();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT active FROM household_memberships WHERE id = ?",
                Boolean.class,
                recipientMember))
        .isEqualTo(serviceFirst);
    if (!operation.equals("create")) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM tasks WHERE id = ? AND household_id = ? AND responsibility_id = ?",
                  Long.class,
                  linked,
                  home,
                  subject))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM responsibilities WHERE id = ?", Long.class, subject))
          .isEqualTo(serviceFirst ? 1L : 0L);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id = ?",
                  Long.class,
                  subject))
          .isEqualTo(serviceFirst && operation.equals("transfer") ? 1L : 0L);
      jdbc.update("DELETE FROM tasks WHERE id = ?", linked);
      jdbc.update(
          "DELETE FROM responsibility_owner_transfers WHERE responsibility_id = ?", subject);
      jdbc.update("DELETE FROM responsibilities WHERE id = ?", subject);
    } else {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM responsibilities WHERE household_id = ? AND title = 'Race service'",
                  Long.class,
                  home))
          .isEqualTo(serviceFirst ? 1L : 0L);
      jdbc.update(
          "DELETE FROM responsibilities WHERE household_id = ? AND title = 'Race service'", home);
    }
    jdbc.update(
        "UPDATE household_memberships SET active = true, slot = 2 WHERE id = ?", recipientMember);
  }

  @Test
  void opposingLockOrderDeadlockRollsBackOneTransactionWithoutBreakingOwnership() throws Exception {
    var householdLocked = new CountDownLatch(1);
    var memberLocked = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var householdFirst =
          pool.submit(
              () -> {
                try {
                  transaction.executeWithoutResult(
                      status -> {
                        jdbc.queryForObject(
                            "SELECT id FROM households WHERE id = ? FOR UPDATE", UUID.class, home);
                        householdLocked.countDown();
                        await(memberLocked);
                        jdbc.queryForObject(
                            "SELECT id FROM household_memberships WHERE id = ? FOR UPDATE",
                            UUID.class,
                            recipientMember);
                      });
                  return true;
                } catch (org.springframework.dao.DataAccessException failure) {
                  return false;
                }
              });
      var memberFirst =
          pool.submit(
              () -> {
                try {
                  transaction.executeWithoutResult(
                      status -> {
                        jdbc.queryForObject(
                            "SELECT id FROM household_memberships WHERE id = ? FOR UPDATE",
                            UUID.class,
                            recipientMember);
                        memberLocked.countDown();
                        await(householdLocked);
                        jdbc.queryForObject(
                            "SELECT id FROM households WHERE id = ? FOR UPDATE", UUID.class, home);
                      });
                  return true;
                } catch (org.springframework.dao.DataAccessException failure) {
                  return false;
                }
              });
      assertThat(
              java.util.List.of(
                  householdFirst.get(20, TimeUnit.SECONDS), memberFirst.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibilities r JOIN household_memberships m ON m.id=r.owner_membership_id AND m.household_id=r.household_id WHERE r.household_id=? AND r.status IN ('ACTIVE','PAUSED') AND NOT m.active",
                Long.class,
                home))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM responsibilities WHERE id = ?", Long.class, responsibility))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE household_id = ?",
                Long.class,
                home))
        .isZero();
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("barrier timed out");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
