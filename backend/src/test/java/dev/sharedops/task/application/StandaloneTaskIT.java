package dev.sharedops.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.TestClockConfiguration;
import dev.sharedops.household.contract.HouseholdTaskAccess;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import dev.sharedops.task.domain.Deadline;
import dev.sharedops.task.domain.TaskPriority;
import dev.sharedops.task.domain.TaskStatus;
import java.time.LocalDate;
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
class StandaloneTaskIT {
  @Autowired TaskService service;
  @Autowired JdbcTemplate jdbc;
  private final UUID home = UUID.randomUUID(), foreignHome = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID(), partner = UUID.randomUUID();
  private final UUID foreign = UUID.randomUUID(), inactive = UUID.randomUUID();
  private final UUID actorMember = UUID.randomUUID(), partnerMember = UUID.randomUUID();

  @BeforeEach
  void setup() {
    for (UUID id : new UUID[] {actor, partner, foreign, inactive}) {
      jdbc.update(
          "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
          id,
          id + "@example.com");
    }
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin'), (?, 'Foreign', 'EUR', 'Europe/Berlin')",
        home,
        foreignHome);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1), (?,?,?,true,2), (?,?,?,true,1), (?,?,?,false,NULL)",
        actorMember,
        actor,
        home,
        partnerMember,
        partner,
        home,
        UUID.randomUUID(),
        foreign,
        foreignHome,
        UUID.randomUUID(),
        inactive,
        home);
    authenticate(actor);
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM tasks WHERE household_id IN (?, ?)", home, foreignHome);
    jdbc.update(
        "DELETE FROM household_memberships WHERE household_id IN (?, ?)", home, foreignHome);
    jdbc.update("DELETE FROM households WHERE id IN (?, ?)", home, foreignHome);
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?, ?, ?)", actor, partner, foreign, inactive);
  }

  @Test
  void titleOnlyCreatesOpenNormalUnassignedAndCanBeRead() {
    var created = service.createStandalone(home, "Buy milk", null, null, null, null);
    assertThat(created.task().responsibilityId()).isNull();
    assertThat(created.task().status()).isEqualTo(TaskStatus.OPEN);
    assertThat(created.task().priority()).isEqualTo(TaskPriority.NORMAL);
    assertThat(created.task().deadline()).isEqualTo(Deadline.none());
    assertThat(created.task().version()).isZero();
    assertThat(created.assigneeAccountId()).isNull();
    assertThat(created.overdue()).isFalse();
    assertThat(service.get(home, created.task().id())).isEqualTo(created);
    assertThat(count()).isEqualTo(1);
  }

  @Test
  void acceptsBothActiveMembersAndResolvesDateUsingHouseholdZone() {
    var a =
        service.createStandalone(
            home,
            "A",
            "notes",
            TaskPriority.HIGH,
            new TaskService.DateDeadline(LocalDate.of(2030, 4, 1)),
            actor);
    var b = service.createStandalone(home, "B", null, TaskPriority.LOW, null, partner);
    assertThat(a.task().assigneeMembershipId()).isEqualTo(actorMember);
    assertThat(a.assigneeAccountId()).isEqualTo(actor);
    assertThat(a.task().deadline())
        .isEqualTo(Deadline.date(LocalDate.of(2030, 4, 1), "Europe/Berlin"));
    assertThat(service.get(home, b.task().id()).assigneeAccountId()).isEqualTo(partner);
    assertThat(count()).isEqualTo(2);
  }

  @Test
  void rejectsInvalidInputsAndForeignReferencesWithoutWrites() {
    assertThatThrownBy(() -> service.createStandalone(home, " ", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.createStandalone(home, "valid", "bad\0value", null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    for (UUID id : new UUID[] {foreign, inactive, UUID.randomUUID()}) {
      assertThatThrownBy(() -> service.createStandalone(home, "valid", null, null, null, id))
          .isInstanceOf(TaskService.InvalidAssignee.class);
    }
    assertThatThrownBy(
            () ->
                service.createStandalone(
                    home, "valid", null, null, new TaskService.DateDeadline(LocalDate.MAX), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(count()).isZero();
  }

  @Test
  void directCallsEnforceActiveHouseholdAndHideForeignTasks() {
    var created = service.createStandalone(home, "private", null, null, null, null);
    assertThatThrownBy(() -> service.get(foreignHome, created.task().id()))
        .isInstanceOf(HouseholdTaskAccess.Inaccessible.class);
    assertThatThrownBy(() -> service.get(home, UUID.randomUUID()))
        .isInstanceOf(TaskService.NotFound.class);
    SecurityContextHolder.clearContext();
    assertThatThrownBy(
            () -> service.createStandalone(home, "unauthenticated", null, null, null, null))
        .isInstanceOf(HouseholdTaskAccess.Unauthenticated.class);
    authenticate(actor);
    jdbc.update(
        "UPDATE household_memberships SET active = false, slot = NULL WHERE id = ?", actorMember);
    assertThatThrownBy(() -> service.createStandalone(home, "inactive", null, null, null, null))
        .isInstanceOf(HouseholdTaskAccess.Inaccessible.class);
    assertThat(count()).isEqualTo(1);
  }

  private int count() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM tasks WHERE household_id = ?", Integer.class, home);
  }

  private static void authenticate(UUID id) {
    var principal = new AccountPrincipal(id, id + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }
}
