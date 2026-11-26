package dev.sharedops.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.TestClockConfiguration;
import dev.sharedops.household.contract.HouseholdTaskAccess;
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
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class LinkedTaskIT {
  @Autowired TaskService service;
  @Autowired JdbcTemplate jdbc;
  final UUID home = UUID.randomUUID(), foreignHome = UUID.randomUUID();
  final UUID actor = UUID.randomUUID(), partner = UUID.randomUUID(), foreign = UUID.randomUUID();
  final UUID inactive = UUID.randomUUID();
  final UUID actorMember = UUID.randomUUID(), partnerMember = UUID.randomUUID();
  final UUID responsibility = UUID.randomUUID(), foreignResponsibility = UUID.randomUUID();

  @BeforeEach
  void setup() {
    for (UUID id : new UUID[] {actor, partner, foreign, inactive}) {
      jdbc.update(
          "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
          id,
          id + "@example.com");
    }
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin'), (?, 'Other', 'EUR', 'Europe/Berlin')",
        home,
        foreignHome);
    UUID foreignMember = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1), (?,?,?,true,2), (?,?,?,true,1), (?,?,?,false,NULL)",
        actorMember,
        actor,
        home,
        partnerMember,
        partner,
        home,
        foreignMember,
        foreign,
        foreignHome,
        UUID.randomUUID(),
        inactive,
        home);
    jdbc.update(
        "INSERT INTO responsibilities (id,household_id,title,mode,status,owner_membership_id,version) VALUES (?,?,'Home responsibility','INDIVIDUAL','ACTIVE',?,0), (?,?,'Foreign responsibility','INDIVIDUAL','ACTIVE',?,0)",
        responsibility,
        home,
        actorMember,
        foreignResponsibility,
        foreignHome,
        foreignMember);
    authenticate(actor);
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM tasks WHERE household_id IN (?, ?)", home, foreignHome);
    jdbc.update("DELETE FROM responsibilities WHERE household_id IN (?, ?)", home, foreignHome);
    jdbc.update(
        "DELETE FROM household_memberships WHERE household_id IN (?, ?)", home, foreignHome);
    jdbc.update("DELETE FROM households WHERE id IN (?, ?)", home, foreignHome);
    jdbc.update("DELETE FROM accounts WHERE id IN (?, ?, ?, ?)", actor, partner, foreign, inactive);
  }

  @Test
  void snapshotsDefaultAndExplicitChoicesAcrossOwnerTransfer() {
    var omitted = create(TaskService.AssignmentChoice.omitted());
    var own = create(TaskService.AssignmentChoice.selected(actor));
    var selected = create(TaskService.AssignmentChoice.selected(partner));
    var unassigned = create(TaskService.AssignmentChoice.selected(null));
    assertThat(omitted.task().responsibilityId()).isEqualTo(responsibility);
    assertThat(omitted.task().assigneeMembershipId()).isEqualTo(actorMember);
    assertThat(own.assigneeAccountId()).isEqualTo(actor);
    assertThat(selected.assigneeAccountId()).isEqualTo(partner);
    assertThat(unassigned.task().assigneeMembershipId()).isNull();
    jdbc.update(
        "UPDATE responsibilities SET owner_membership_id = ?, version = version + 1 WHERE id = ?",
        partnerMember,
        responsibility);
    assertThat(service.get(home, omitted.task().id()).assigneeAccountId()).isEqualTo(actor);
    assertThat(create(TaskService.AssignmentChoice.omitted()).assigneeAccountId())
        .isEqualTo(partner);
    assertThat(count()).isEqualTo(5);
  }

  @Test
  void rejectsForeignMissingAndInactiveContextsWithoutWrites() {
    assertThatThrownBy(
            () -> createFor(foreignResponsibility, TaskService.AssignmentChoice.omitted()))
        .isInstanceOf(TaskService.NotFound.class);
    assertThatThrownBy(() -> createFor(UUID.randomUUID(), TaskService.AssignmentChoice.omitted()))
        .isInstanceOf(TaskService.NotFound.class);
    for (String status : new String[] {"PAUSED", "ARCHIVED"}) {
      jdbc.update("UPDATE responsibilities SET status = ? WHERE id = ?", status, responsibility);
      assertThatThrownBy(() -> create(TaskService.AssignmentChoice.omitted()))
          .isInstanceOf(TaskService.InvalidState.class);
    }
    jdbc.update("UPDATE responsibilities SET status = 'ACTIVE' WHERE id = ?", responsibility);
    for (UUID account : new UUID[] {foreign, inactive, UUID.randomUUID()}) {
      assertThatThrownBy(() -> create(TaskService.AssignmentChoice.selected(account)))
          .isInstanceOf(TaskService.InvalidAssignee.class);
    }
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> create(TaskService.AssignmentChoice.omitted()))
        .isInstanceOf(HouseholdTaskAccess.Unauthenticated.class);
    assertThat(count()).isZero();
  }

  private TaskService.TaskView create(TaskService.AssignmentChoice choice) {
    return createFor(responsibility, choice);
  }

  private TaskService.TaskView createFor(UUID id, TaskService.AssignmentChoice choice) {
    return service.createLinked(home, id, "Task", null, null, null, choice);
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
