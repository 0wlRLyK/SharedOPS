package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.contract.HouseholdResponsibilityAccess;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ResponsibilityEligibilityIT {
  @Autowired HouseholdResponsibilityAccess access;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;
  @Autowired TransactionTemplate transactions;
  private UUID actor;
  private UUID other;
  private UUID inactive;
  private UUID home;
  private UUID foreignHome;
  private UUID actorMembership;

  @BeforeEach
  void setup() {
    actor = UUID.randomUUID();
    other = UUID.randomUUID();
    inactive = UUID.randomUUID();
    home = UUID.randomUUID();
    foreignHome = UUID.randomUUID();
    actorMembership = UUID.randomUUID();
    for (UUID id : new UUID[] {actor, other, inactive}) {
      jdbc.update(
          "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
          id,
          id + "@example.com");
    }
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin'), (?, 'Other', 'EUR', 'Europe/Berlin')",
        home,
        foreignHome);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1), (?,?,?,true,1), (?,?,?,false,NULL)",
        actorMembership,
        actor,
        home,
        UUID.randomUUID(),
        other,
        foreignHome,
        UUID.randomUUID(),
        inactive,
        home);
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update(
        "DELETE FROM household_memberships WHERE household_id IN (?, ?)", home, foreignHome);
    jdbc.update("DELETE FROM households WHERE id IN (?, ?)", home, foreignHome);
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?, ?)", actor, other, inactive);
  }

  @Test
  void activeActorCanResolveOnlyActiveSameHouseholdMemberships() {
    authenticate(actor);
    transactions.executeWithoutResult(
        tx ->
            access.withLockedHousehold(
                home,
                household -> {
                  assertThat(household.actorAccountId()).isEqualTo(actor);
                  assertThat(household.activeMembershipId(actor)).contains(actorMembership);
                  assertThat(household.activeMembershipId(other)).isEmpty();
                  assertThat(household.activeMembershipId(inactive)).isEmpty();
                  assertThat(household.activeMembershipId(UUID.randomUUID())).isEmpty();
                  assertThat(household.activeMembershipId(null)).isEmpty();
                  return null;
                }));
  }

  @Test
  void foreignMissingAndInactiveActorAreIndistinguishableAndNeverInvokeCallback() {
    authenticate(actor);
    assertInaccessible(foreignHome);
    assertInaccessible(UUID.randomUUID());
    assertInaccessible(null);
    jdbc.update(
        "UPDATE household_memberships SET active = false, slot = NULL WHERE id = ?",
        actorMembership);
    assertInaccessible(home);
    assertThat(
            jdbc.queryForObject(
                "SELECT active FROM household_memberships WHERE id = ?",
                Boolean.class,
                actorMembership))
        .isFalse();
  }

  @Test
  void requiresAuthenticationAndAnExistingTransaction() {
    assertThatThrownBy(() -> access.withLockedHousehold(home, h -> null))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(
            () -> transactions.execute(status -> access.withLockedHousehold(home, h -> null)))
        .isInstanceOf(HouseholdResponsibilityAccess.Unauthenticated.class);
  }

  @Test
  void lockSurvivesCallbackUntilOuterTransactionEndsAndViewCannotEscape() throws Exception {
    authenticate(actor);
    var escaped = new AtomicReference<HouseholdResponsibilityAccess.LockedHousehold>();
    transactions.executeWithoutResult(
        tx -> {
          access.withLockedHousehold(
              home,
              household -> {
                escaped.set(household);
                assertThat(household.actorAccountId()).isEqualTo(actor);
                return null;
              });
          assertThatThrownBy(() -> escaped.get().activeMembershipId(actor))
              .isInstanceOf(IllegalStateException.class);
          assertRowLockedByAnotherTransaction();
        });
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement("SELECT id FROM households WHERE id = ? FOR UPDATE NOWAIT")) {
        statement.setObject(1, home);
        try (var result = statement.executeQuery()) {
          assertThat(result.next()).isTrue();
        }
      } finally {
        connection.rollback();
      }
    }
  }

  private void assertRowLockedByAnotherTransaction() {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement("SELECT id FROM households WHERE id = ? FOR UPDATE NOWAIT")) {
        statement.setObject(1, home);
        assertThatThrownBy(statement::executeQuery)
            .isInstanceOf(SQLException.class)
            .extracting(e -> ((SQLException) e).getSQLState())
            .isEqualTo("55P03");
      } finally {
        connection.rollback();
      }
    } catch (SQLException e) {
      throw new AssertionError(e);
    }
  }

  private void assertInaccessible(UUID target) {
    assertThatThrownBy(
            () ->
                transactions.execute(
                    status ->
                        access.withLockedHousehold(
                            target,
                            household -> {
                              throw new AssertionError("unauthorized callback");
                            })))
        .isInstanceOf(HouseholdResponsibilityAccess.Inaccessible.class);
  }

  private static void authenticate(UUID id) {
    var principal = new AccountPrincipal(id, id + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }
}
