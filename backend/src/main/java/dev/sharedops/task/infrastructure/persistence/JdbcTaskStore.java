package dev.sharedops.task.infrastructure.persistence;

import dev.sharedops.task.application.TaskStore;
import dev.sharedops.task.domain.Deadline;
import dev.sharedops.task.domain.Task;
import dev.sharedops.task.domain.TaskPriority;
import dev.sharedops.task.domain.TaskStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL storage, called only in an authorized application transaction. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcTaskStore implements TaskStore {
  private static final String COLUMNS =
      "id, household_id, responsibility_id, title, description, priority, status, version, "
          + "assignee_membership_id, deadline_kind, due_date_iso, due_zone, due_epoch_second, due_nano";
  private final JdbcTemplate jdbc;

  public JdbcTaskStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Task task) {
    Objects.requireNonNull(task, "task");
    if (task.status() != TaskStatus.OPEN || task.version() != 0) {
      throw new IllegalArgumentException("A new task must be open at version zero");
    }
    DeadlineColumns due = columns(task.deadline());
    jdbc.update(
        "INSERT INTO tasks (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        task.id(),
        task.householdId(),
        task.responsibilityId(),
        task.title(),
        task.description(),
        task.priority().name(),
        task.status().name(),
        task.version(),
        task.assigneeMembershipId(),
        due.kind(),
        due.date(),
        due.zone(),
        due.seconds(),
        due.nano());
  }

  @Override
  public Optional<Task> lock(UUID householdId, UUID taskId) {
    return jdbc
        .query(
            "SELECT " + COLUMNS + " FROM tasks WHERE household_id = ? AND id = ? FOR UPDATE",
            (rs, row) -> map(rs),
            householdId,
            taskId)
        .stream()
        .findFirst();
  }

  @Override
  public boolean update(Task task, long expectedVersion, EditFact fact) {
    Objects.requireNonNull(task, "task");
    Objects.requireNonNull(fact, "fact");
    if (expectedVersion < 0
        || expectedVersion == Long.MAX_VALUE
        || task.version() != expectedVersion + 1
        || task.status() != TaskStatus.OPEN
        || !task.id().equals(fact.taskId())
        || !task.householdId().equals(fact.householdId())
        || fact.previousVersion() != expectedVersion
        || fact.newVersion() != task.version()) {
      throw new IllegalArgumentException(
          "Task and edit fact must describe the same versioned edit");
    }
    DeadlineColumns due = columns(task.deadline());
    int changed =
        jdbc.update(
            "UPDATE tasks SET title=?, description=?, priority=?, assignee_membership_id=?, "
                + "deadline_kind=?, due_date_iso=?, due_zone=?, due_epoch_second=?, due_nano=?, version=? "
                + "WHERE household_id=? AND id=? AND version=? AND status='OPEN' "
                + "AND responsibility_id IS NOT DISTINCT FROM ?",
            task.title(),
            task.description(),
            task.priority().name(),
            task.assigneeMembershipId(),
            due.kind(),
            due.date(),
            due.zone(),
            due.seconds(),
            due.nano(),
            task.version(),
            task.householdId(),
            task.id(),
            expectedVersion,
            task.responsibilityId());
    if (changed == 0) {
      return false;
    }
    jdbc.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  "INSERT INTO task_edit_facts (id, task_id, household_id, actor_account_id, occurred_at, "
                      + "previous_version, new_version, changed_fields) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
          statement.setObject(1, fact.id());
          statement.setObject(2, fact.taskId());
          statement.setObject(3, fact.householdId());
          statement.setObject(4, fact.actorAccountId());
          statement.setTimestamp(5, Timestamp.from(fact.occurredAt()));
          statement.setLong(6, fact.previousVersion());
          statement.setLong(7, fact.newVersion());
          statement.setArray(
              8,
              connection.createArrayOf(
                  "text",
                  fact.changedFields().stream().map(Enum::name).sorted().toArray(String[]::new)));
          return statement;
        });
    return true;
  }

  private static Task map(ResultSet rs) throws SQLException {
    String kind = rs.getString("deadline_kind");
    Deadline deadline =
        switch (kind) {
          case "NONE" -> Deadline.none();
          case "DATE" -> Deadline.date(rs.getString("due_date_iso"), rs.getString("due_zone"));
          case "DATE_TIME" ->
              Deadline.dateTime(
                  Instant.ofEpochSecond(rs.getLong("due_epoch_second"), rs.getInt("due_nano")));
          default -> throw new IllegalStateException("Unknown stored task deadline kind");
        };
    return new Task(
        rs.getObject("id", UUID.class),
        rs.getObject("household_id", UUID.class),
        rs.getObject("responsibility_id", UUID.class),
        rs.getString("title"),
        rs.getString("description"),
        TaskPriority.valueOf(rs.getString("priority")),
        TaskStatus.valueOf(rs.getString("status")),
        deadline,
        rs.getObject("assignee_membership_id", UUID.class),
        rs.getLong("version"));
  }

  private static DeadlineColumns columns(Deadline deadline) {
    return switch (deadline) {
      case Deadline.None ignored -> new DeadlineColumns("NONE", null, null, null, null);
      case Deadline.Date date ->
          new DeadlineColumns(
              "DATE", date.dueDate().toString(), date.householdZone().getId(), null, null);
      case Deadline.DateTime dateTime ->
          new DeadlineColumns(
              "DATE_TIME",
              null,
              null,
              dateTime.instant().getEpochSecond(),
              dateTime.instant().getNano());
    };
  }

  private record DeadlineColumns(
      String kind, String date, String zone, Long seconds, Integer nano) {}
}
