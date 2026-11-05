package dev.sharedops.task.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
public class ChecklistPositionRaceIT {
  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired TransactionTemplate transactionTemplate;

  @Test
  void rejectsConcurrentDuplicateChecklistPositions() throws Exception {
    UUID householdId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ConcurrentLinkedQueue<Long> transactionIds = new ConcurrentLinkedQueue<>();

    try {
      jdbcTemplate.update("INSERT INTO households (id) VALUES (?)", householdId);

      jdbcTemplate.update(
          "INSERT INTO tasks (id, household_id) VALUES (?, ?)", taskId, householdId);

      Runnable worker =
          () -> {
            transactionTemplate.executeWithoutResult(
                status -> {
                  jdbcTemplate.execute("SET LOCAL statement_timeout = '5s'");
                  transactionIds.add(
                      jdbcTemplate.queryForObject("SELECT txid_current()", Long.class));
                  ready.countDown();

                  try {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                      throw new IllegalStateException("Start signal timeout");
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Worker interrupted", e);
                  }

                  UUID entryId = UUID.randomUUID();

                  jdbcTemplate.update(
                      """
          INSERT INTO checklist_entries
              (id, task_id, text, is_completed, position)
          VALUES (?, ?, ?, ?, ?)
          """,
                      entryId,
                      taskId,
                      "Concurrent entry",
                      false,
                      0);
                });
          };

      ExecutorService executor = Executors.newFixedThreadPool(2);
      Future<?> futureA = null;
      Future<?> futureB = null;
      try {
        futureA = executor.submit(worker);
        futureB = executor.submit(worker);

        try {
          assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
          assertThat(transactionIds).hasSize(2).doesNotHaveDuplicates();
        } finally {
          start.countDown();
        }

        int successes = 0;
        int duplicateFailures = 0;

        for (Future<?> future : List.of(futureA, futureB)) {
          try {
            future.get(5, TimeUnit.SECONDS);
            successes++;
          } catch (ExecutionException e) {
            if (e.getCause() instanceof DuplicateKeyException) {
              duplicateFailures++;
            } else {
              throw e;
            }
          }
        }
        assertThat(successes).isEqualTo(1);
        assertThat(duplicateFailures).isEqualTo(1);
        assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM checklist_entries WHERE task_id = ? AND position = 0",
                    Integer.class,
                    taskId))
            .isEqualTo(1);
      } finally {
        start.countDown();
        if (futureA != null) {
          futureA.cancel(true);
        }
        if (futureB != null) {
          futureB.cancel(true);
        }
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
            .as("race workers terminate")
            .isTrue();
      }
    } finally {
      jdbcTemplate.update("DELETE FROM checklist_entries WHERE task_id = ?", taskId);
      jdbcTemplate.update("DELETE FROM tasks where id = ?;", taskId);
      jdbcTemplate.update("DELETE FROM households where id = ?;", householdId);
    }
  }
}
