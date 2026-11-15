package dev.sharedops.household.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.application.RegisterAccountService;
import dev.sharedops.household.domain.exception.EmailAlreadyRegisteredException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaAccountRegistrationIT {

  @Autowired RegisterAccountService registerAccountService;

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void databaseRejectsNonCanonicalEmail() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO accounts (id, email, password_hash) VALUES (?, ?, ?)",
                    UUID.randomUUID(),
                    " Mixed@example.com ",
                    "hash"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void duplicateNormalizedEmailIsRejected() {
    String email = UUID.randomUUID() + "@example.com";
    try {

      registerAccountService.register("                      " + email + "   ", "password");
      assertThatThrownBy(
              () ->
                  registerAccountService.register(
                      "                      " + email.toUpperCase(Locale.ROOT) + "   ",
                      "password2"))
          .isInstanceOf(EmailAlreadyRegisteredException.class);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM accounts WHERE email = ? ", Integer.class, email))
          .isEqualTo(1);
    } finally {
      jdbcTemplate.update("DELETE FROM accounts WHERE email = ?", email);
    }
  }

  @Test
  void concurrentDuplicateNormalizedEmailIsRejected() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<?>> futures =
          List.of(
              executor.submit(() -> registerAfterStart(email, ready, start)),
              executor.submit(
                  () ->
                      registerAfterStart(
                          "  " + email.toUpperCase(Locale.ROOT) + "  ", ready, start)));
      try {
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      } finally {
        start.countDown();
      }

      int successes = 0;
      int duplicateFailures = 0;
      for (Future<?> future : futures) {
        try {
          future.get(10, TimeUnit.SECONDS);
          successes++;
        } catch (ExecutionException e) {
          if (e.getCause() instanceof EmailAlreadyRegisteredException) {
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
                  "SELECT COUNT(*) FROM accounts WHERE email = ?", Integer.class, email))
          .isEqualTo(1);
    } finally {
      start.countDown();
      executor.shutdownNow();
      try {
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        jdbcTemplate.update("DELETE FROM accounts WHERE email = ?", email);
      }
    }
  }

  private void registerAfterStart(String email, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      if (!start.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Start signal timeout");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Worker interrupted", e);
    }
    registerAccountService.register(email, "password");
  }
}
