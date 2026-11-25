package dev.sharedops.responsibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.contract.HouseholdResponsibilityAccess;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import dev.sharedops.responsibility.domain.Responsibility;
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
class ResponsibilityCreateReadIT {
  @Autowired ResponsibilityService service;
  @Autowired JdbcTemplate jdbc;
  UUID actor = UUID.randomUUID();
  UUID other = UUID.randomUUID();
  UUID inactive = UUID.randomUUID();
  UUID home = UUID.randomUUID();
  UUID foreignHome = UUID.randomUUID();
  UUID membership = UUID.randomUUID();

  @BeforeEach
  void setup() {
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
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?, ?, ?, true, 1), (?, ?, ?, true, 1), (?, ?, ?, false, NULL)",
        membership,
        actor,
        home,
        UUID.randomUUID(),
        other,
        foreignHome,
        UUID.randomUUID(),
        inactive,
        home);
    authenticate(actor);
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
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?, ?)", actor, other, inactive);
  }

  private void authenticate(UUID id) {
    var principal = new AccountPrincipal(id, id + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }

  @Test
  void createAndQueriesUseActiveMembershipAndSeparateArchivedRecords() {
    Responsibility created = service.create(home, "Plants", "", actor);
    assertThat(created.ownerMembershipId()).isEqualTo(membership);
    assertThat(created.version()).isZero();
    assertThat(service.get(home, created.id())).isEqualTo(created);
    assertThat(service.list(home, false)).containsExactly(created);
    assertThat(service.list(home, true)).isEmpty();
    jdbc.update("UPDATE responsibilities SET status = 'PAUSED' WHERE id = ?", created.id());
    assertThat(service.list(home, false)).hasSize(1);
    jdbc.update("UPDATE responsibilities SET status = 'ARCHIVED' WHERE id = ?", created.id());
    assertThat(service.list(home, false)).isEmpty();
    assertThat(service.list(home, true)).hasSize(1);
  }

  @Test
  void invalidOwnersAndBlankTitleLeaveNoRows() {
    for (UUID owner : new UUID[] {other, inactive, UUID.randomUUID()}) {
      assertThatThrownBy(() -> service.create(home, "Title", null, owner))
          .isInstanceOf(ResponsibilityService.InvalidOwner.class);
    }
    assertThatThrownBy(() -> service.create(home, " ", null, actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibilities WHERE household_id = ?", Long.class, home))
        .isZero();
  }

  @Test
  void foreignAndMissingResourcesAreIndistinguishableAndMembershipIsRequired() {
    Responsibility created = service.create(home, "Title", null, actor);
    authenticate(other);
    assertThatThrownBy(() -> service.get(foreignHome, created.id()))
        .isInstanceOf(ResponsibilityService.NotFound.class);
    assertThatThrownBy(() -> service.get(foreignHome, UUID.randomUUID()))
        .isInstanceOf(ResponsibilityService.NotFound.class);
    assertThatThrownBy(() -> service.get(home, created.id()))
        .isInstanceOf(HouseholdResponsibilityAccess.Inaccessible.class);
    authenticate(inactive);
    assertThatThrownBy(() -> service.list(home, false))
        .isInstanceOf(HouseholdResponsibilityAccess.Inaccessible.class);
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> service.list(home, false))
        .isInstanceOf(HouseholdResponsibilityAccess.Unauthenticated.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibilities WHERE id = ?", Long.class, created.id()))
        .isEqualTo(1);
  }
}
