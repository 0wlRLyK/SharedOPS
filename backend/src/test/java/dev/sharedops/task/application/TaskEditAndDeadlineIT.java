package dev.sharedops.task.application;

import static dev.sharedops.task.application.TaskService.Field.omitted;
import static dev.sharedops.task.application.TaskService.Field.supplied;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.TestClockConfiguration;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import dev.sharedops.task.domain.Deadline;
import dev.sharedops.task.domain.TaskPriority;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
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
class TaskEditAndDeadlineIT {
  @Autowired TaskService service;
  @Autowired JdbcTemplate jdbc;
  private final UUID home = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      partner = UUID.randomUUID();
  private final UUID actorMember = UUID.randomUUID(), partnerMember = UUID.randomUUID();

  @BeforeEach
  void setup() {
    for (UUID id : new UUID[] {actor, partner}) {
      jdbc.update(
          "INSERT INTO accounts (id,email,password_hash) VALUES (?, ?, 'hash')",
          id,
          id + "@example.com");
    }
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin')",
        home);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1),(?,?,?,true,2)",
        actorMember,
        actor,
        home,
        partnerMember,
        partner,
        home);
    var principal = new AccountPrincipal(actor, actor + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM task_edit_facts WHERE household_id = ?", home);
    jdbc.update("DELETE FROM tasks WHERE household_id = ?", home);
    jdbc.update("DELETE FROM household_memberships WHERE household_id = ?", home);
    jdbc.update("DELETE FROM households WHERE id = ?", home);
    jdbc.update("DELETE FROM accounts WHERE id IN (?,?)", actor, partner);
  }

  @Test
  void updatesAndClearsFieldsAtomicallyAndRecordsSuppliedFields() {
    var created =
        service.createStandalone(
            home,
            "Original",
            "notes",
            TaskPriority.HIGH,
            new TaskService.DateDeadline(LocalDate.of(2026, 10, 24)),
            partner);
    var edited =
        service.edit(
            home,
            created.task().id(),
            new TaskService.EditCommand(
                0,
                supplied("Changed"),
                supplied(null),
                supplied(TaskPriority.LOW),
                supplied(new TaskService.NoDeadline()),
                supplied(null)));
    assertThat(edited.task().title()).isEqualTo("Changed");
    assertThat(edited.task().description()).isNull();
    assertThat(edited.task().priority()).isEqualTo(TaskPriority.LOW);
    assertThat(edited.task().deadline()).isEqualTo(Deadline.none());
    assertThat(edited.assigneeAccountId()).isNull();
    assertThat(edited.task().version()).isEqualTo(1);
    assertThat(edited.overdue()).isFalse();
    assertThat(service.get(home, created.task().id())).isEqualTo(edited);
    assertThat(facts()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT cardinality(changed_fields) FROM task_edit_facts WHERE task_id = ?",
                Integer.class,
                created.task().id()))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT actor_account_id FROM task_edit_facts WHERE task_id = ?",
                UUID.class,
                created.task().id()))
        .isEqualTo(actor);
    var equal =
        service.edit(
            home,
            created.task().id(),
            new TaskService.EditCommand(
                1, supplied("Changed"), omitted(), omitted(), omitted(), omitted()));
    assertThat(equal.task().version()).isEqualTo(2);
    assertThat(equal.task().priority()).isEqualTo(TaskPriority.LOW);
    assertThat(facts()).isEqualTo(2);
  }

  @Test
  void failedEditsLeaveTaskAndFactsUnchanged() {
    var created = service.createStandalone(home, "Original", "notes", null, null, actor);
    UUID id = created.task().id();
    assertThatThrownBy(
            () ->
                service.edit(
                    home,
                    id,
                    new TaskService.EditCommand(
                        1, supplied("stale"), omitted(), omitted(), omitted(), omitted())))
        .isInstanceOf(TaskVersionConflictException.class);
    assertThatThrownBy(
            () ->
                service.edit(
                    home,
                    id,
                    new TaskService.EditCommand(
                        0, supplied(" "), omitted(), omitted(), omitted(), omitted())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.edit(
                    home,
                    id,
                    new TaskService.EditCommand(
                        0,
                        omitted(),
                        omitted(),
                        omitted(),
                        omitted(),
                        supplied(UUID.randomUUID()))))
        .isInstanceOf(TaskService.InvalidAssignee.class);
    assertThatThrownBy(
            () ->
                new TaskService.EditCommand(
                    0, supplied(null), omitted(), omitted(), omitted(), omitted()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new TaskService.EditCommand(
                    0, omitted(), omitted(), supplied(null), omitted(), omitted()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new TaskService.EditCommand(
                    0, omitted(), omitted(), omitted(), omitted(), omitted()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.edit(
                    home,
                    id,
                    new TaskService.EditCommand(
                        0,
                        omitted(),
                        omitted(),
                        omitted(),
                        supplied(
                            new TaskService.LocalDeadline(
                                LocalDateTime.of(2026, 3, 29, 2, 30), Optional.empty())),
                        omitted())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.edit(
                    home,
                    id,
                    new TaskService.EditCommand(
                        0,
                        omitted(),
                        omitted(),
                        omitted(),
                        supplied(
                            new TaskService.LocalDeadline(
                                LocalDateTime.of(2026, 10, 25, 2, 30), Optional.empty())),
                        omitted())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.get(home, id)).isEqualTo(created);
    assertThat(facts()).isZero();
    jdbc.update("UPDATE tasks SET status = 'COMPLETED' WHERE id = ?", id);
    assertThatThrownBy(
            () ->
                service.edit(
                    home,
                    id,
                    new TaskService.EditCommand(
                        0, supplied("no"), omitted(), omitted(), omitted(), omitted())))
        .isInstanceOf(TaskService.InvalidState.class);
    assertThat(facts()).isZero();
    assertThat(service.get(home, id).overdue()).isFalse();
  }

  @Test
  void preservesNanosecondsAndDerivesDeadlineOverdueWithInjectedClock() {
    var created = service.createStandalone(home, "Timed", null, null, null, null);
    Instant beforeClock = Instant.parse("2026-10-25T11:59:59.123456789Z");
    var edited =
        service.edit(
            home,
            created.task().id(),
            new TaskService.EditCommand(
                0,
                omitted(),
                omitted(),
                omitted(),
                supplied(new TaskService.InstantDeadline(beforeClock)),
                omitted()));
    assertThat(edited.task().deadline()).isEqualTo(Deadline.dateTime(beforeClock));
    assertThat(service.get(home, created.task().id()).task().deadline())
        .isEqualTo(Deadline.dateTime(beforeClock));
    assertThat(edited.overdue()).isTrue();
    var exact =
        service.edit(
            home,
            created.task().id(),
            new TaskService.EditCommand(
                1,
                omitted(),
                omitted(),
                omitted(),
                supplied(new TaskService.InstantDeadline(Instant.parse("2026-10-25T12:00:00Z"))),
                omitted()));
    assertThat(exact.overdue()).isFalse();
    var previousDate =
        service.edit(
            home,
            created.task().id(),
            new TaskService.EditCommand(
                2,
                omitted(),
                omitted(),
                omitted(),
                supplied(new TaskService.DateDeadline(LocalDate.of(2026, 10, 24))),
                omitted()));
    assertThat(previousDate.overdue()).isTrue();
    var today =
        service.edit(
            home,
            created.task().id(),
            new TaskService.EditCommand(
                3,
                omitted(),
                omitted(),
                omitted(),
                supplied(new TaskService.DateDeadline(LocalDate.of(2026, 10, 25))),
                omitted()));
    assertThat(today.overdue()).isFalse();
  }

  private int facts() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM task_edit_facts WHERE household_id = ?", Integer.class, home);
  }
}
