package dev.sharedops.responsibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.contract.HouseholdResponsibilityAccess;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
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
@Import(TestcontainersConfiguration.class)
class ResponsibilityIsolationIT {
  @Autowired ResponsibilityService service;
  @Autowired JdbcTemplate jdbc;
  private final UUID actor = UUID.randomUUID();
  private final UUID foreignActor = UUID.randomUUID();
  private final UUID inactive = UUID.randomUUID();
  private final UUID home = UUID.randomUUID();
  private final UUID foreignHome = UUID.randomUUID();
  private final UUID owner = UUID.randomUUID();
  private final UUID responsibility = UUID.randomUUID();

  @BeforeEach
  void setup() {
    for (UUID account : new UUID[] {actor, foreignActor, inactive}) {
      jdbc.update(
          "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
          account,
          account + "@example.com");
    }
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin'), (?, 'Other', 'EUR', 'Europe/Berlin')",
        home,
        foreignHome);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1), (?,?,?,true,1), (?,?,?,false,NULL)",
        owner,
        actor,
        home,
        UUID.randomUUID(),
        foreignActor,
        foreignHome,
        UUID.randomUUID(),
        inactive,
        home);
    jdbc.update(
        "INSERT INTO responsibilities (id,household_id,title,description,mode,status,owner_membership_id,version) VALUES (?,?,'Original',NULL,'INDIVIDUAL','ACTIVE',?,0)",
        responsibility,
        home,
        owner);
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update(
        "DELETE FROM responsibility_owner_transfers WHERE household_id IN (?, ?)",
        home,
        foreignHome);
    jdbc.update("DELETE FROM responsibilities WHERE household_id IN (?, ?)", home, foreignHome);
    jdbc.update(
        "DELETE FROM household_memberships WHERE household_id IN (?, ?)", home, foreignHome);
    jdbc.update("DELETE FROM households WHERE id IN (?, ?)", home, foreignHome);
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?, ?)", actor, foreignActor, inactive);
  }

  @Test
  void foreignAndMissingResourcesAreIndistinguishableForAnAuthorizedMember() {
    authenticate(foreignActor);
    assertThatThrownBy(() -> service.get(foreignHome, responsibility))
        .isInstanceOf(ResponsibilityService.NotFound.class);
    assertThatThrownBy(() -> service.get(foreignHome, UUID.randomUUID()))
        .isInstanceOf(ResponsibilityService.NotFound.class);
    assertThatThrownBy(() -> service.edit(foreignHome, responsibility, 0, "Changed", null))
        .isInstanceOf(ResponsibilityService.NotFound.class);
    assertUnchanged();
  }

  @Test
  void foreignInactiveAndUnauthenticatedActorsCannotMutateOrReadHousehold() {
    authenticate(foreignActor);
    assertDenied();
    authenticate(inactive);
    assertDenied();
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> service.edit(home, responsibility, 0, "Changed", null))
        .isInstanceOf(HouseholdResponsibilityAccess.Unauthenticated.class);
    assertThatThrownBy(() -> service.list(home, false))
        .isInstanceOf(HouseholdResponsibilityAccess.Unauthenticated.class);
    assertUnchanged();
  }

  private void assertDenied() {
    assertThatThrownBy(() -> service.get(home, responsibility))
        .isInstanceOf(HouseholdResponsibilityAccess.Inaccessible.class);
    assertThatThrownBy(() -> service.edit(home, responsibility, 0, "Changed", null))
        .isInstanceOf(HouseholdResponsibilityAccess.Inaccessible.class);
    assertThatThrownBy(() -> service.transfer(home, responsibility, 0, foreignActor))
        .isInstanceOf(HouseholdResponsibilityAccess.Inaccessible.class);
    assertUnchanged();
  }

  private void assertUnchanged() {
    var row =
        jdbc.queryForMap(
            "SELECT title, status, owner_membership_id, version FROM responsibilities WHERE id = ?",
            responsibility);
    assertThat(row.get("title")).isEqualTo("Original");
    assertThat(row.get("status")).isEqualTo("ACTIVE");
    assertThat(row.get("owner_membership_id")).isEqualTo(owner);
    assertThat(((Number) row.get("version")).longValue()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id = ?",
                Long.class,
                responsibility))
        .isZero();
  }

  private static void authenticate(UUID id) {
    var principal = new AccountPrincipal(id, id + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }
}
