package dev.sharedops.responsibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.responsibility.contract.ResponsibilityTaskAccess;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ResponsibilityTaskAccessIT {
  @Autowired ResponsibilityTaskAccess access;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transactions;

  final UUID account = UUID.randomUUID();
  final UUID household = UUID.randomUUID();
  final UUID otherHousehold = UUID.randomUUID();
  final UUID member = UUID.randomUUID();
  final UUID responsibility = UUID.randomUUID();

  @BeforeEach
  void setup() {
    jdbc.update(
        "INSERT INTO accounts (id, email, password_hash) VALUES (?, ?, 'hash')",
        account,
        account + "@example.com");
    jdbc.update(
        "INSERT INTO households (id, name, currency, timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin'), (?, 'Other', 'EUR', 'Europe/Berlin')",
        household,
        otherHousehold);
    jdbc.update(
        "INSERT INTO household_memberships (id, account_id, household_id, active, slot) VALUES (?, ?, ?, true, 1)",
        member,
        account,
        household);
    jdbc.update(
        "INSERT INTO responsibilities (id, household_id, title, mode, status, owner_membership_id, version) VALUES (?, ?, 'Plants', 'INDIVIDUAL', 'ACTIVE', ?, 0)",
        responsibility,
        household,
        member);
  }

  @AfterEach
  void cleanup() {
    jdbc.update(
        "DELETE FROM responsibilities WHERE household_id IN (?, ?)", household, otherHousehold);
    jdbc.update(
        "DELETE FROM household_memberships WHERE household_id IN (?, ?)",
        household,
        otherHousehold);
    jdbc.update("DELETE FROM households WHERE id IN (?, ?)", household, otherHousehold);
    jdbc.update("DELETE FROM accounts WHERE id = ?", account);
  }

  @Test
  void returnsLifecycleSnapshotsAndHidesForeignAndMissingReferences() {
    for (var status : ResponsibilityTaskAccess.Status.values()) {
      jdbc.update(
          "UPDATE responsibilities SET status = ? WHERE id = ?", status.name(), responsibility);
      transactions.executeWithoutResult(
          ignored -> {
            lockHousehold(household);
            assertThat(access.lockForTask(household, responsibility))
                .contains(
                    new ResponsibilityTaskAccess.Snapshot(
                        member, status, ResponsibilityTaskAccess.Mode.INDIVIDUAL));
            assertThat(access.lockForTask(household, UUID.randomUUID())).isEmpty();
          });
    }
    transactions.executeWithoutResult(
        ignored -> {
          lockHousehold(otherHousehold);
          assertThat(access.lockForTask(otherHousehold, responsibility)).isEmpty();
          assertThat(access.lockForTask(otherHousehold, UUID.randomUUID())).isEmpty();
        });
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibilities WHERE id = ?", Long.class, responsibility))
        .isEqualTo(1L);
  }

  @Test
  void requiresExistingTransaction() {
    assertThatThrownBy(() -> access.lockForTask(household, responsibility))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void snapshotRowLockIsHeldUntilCallerTransactionCompletes() throws Exception {
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var updated = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var holder =
          executor.submit(
              () ->
                  transactions.executeWithoutResult(
                      ignored -> {
                        lockHousehold(household);
                        assertThat(access.lockForTask(household, responsibility)).isPresent();
                        locked.countDown();
                        await(release);
                      }));
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
      var writer =
          executor.submit(
              () -> {
                jdbc.update(
                    "UPDATE responsibilities SET status = 'PAUSED' WHERE id = ?", responsibility);
                updated.countDown();
              });
      try {
        assertThat(updated.await(200, TimeUnit.MILLISECONDS)).isFalse();
      } finally {
        release.countDown();
      }
      holder.get(5, TimeUnit.SECONDS);
      writer.get(5, TimeUnit.SECONDS);
      assertThat(updated.getCount()).isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM responsibilities WHERE id = ?", String.class, responsibility))
          .isEqualTo("PAUSED");
    }
  }

  private void lockHousehold(UUID id) {
    jdbc.queryForObject("SELECT id FROM households WHERE id = ? FOR UPDATE", UUID.class, id);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for transaction release");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
