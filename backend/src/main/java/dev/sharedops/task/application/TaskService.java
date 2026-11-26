package dev.sharedops.task.application;

import dev.sharedops.household.contract.HouseholdTaskAccess;
import dev.sharedops.household.contract.HouseholdTaskAccess.LockedHousehold;
import dev.sharedops.responsibility.contract.ResponsibilityTaskAccess;
import dev.sharedops.task.application.TaskStore.EditFact;
import dev.sharedops.task.application.TaskStore.EditedField;
import dev.sharedops.task.domain.Deadline;
import dev.sharedops.task.domain.Task;
import dev.sharedops.task.domain.TaskPriority;
import dev.sharedops.task.domain.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary for authorized household task operations. */
@Service
public class TaskService {
  private final HouseholdTaskAccess households;
  private final ResponsibilityTaskAccess responsibilities;
  private final TaskStore store;
  private final Clock clock;

  public TaskService(
      HouseholdTaskAccess households,
      ResponsibilityTaskAccess responsibilities,
      TaskStore store,
      Clock clock) {
    this.households = Objects.requireNonNull(households);
    this.responsibilities = Objects.requireNonNull(responsibilities);
    this.store = Objects.requireNonNull(store);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Null priority and deadline represent omitted fields; explicit null is rejected by transport.
   */
  @Transactional
  public TaskView createStandalone(
      UUID householdId,
      String title,
      String description,
      TaskPriority priority,
      DeadlineInput deadline,
      UUID assigneeAccountId) {
    Objects.requireNonNull(householdId, "householdId");
    return households.withLockedHousehold(
        householdId,
        household -> {
          UUID assignee = resolveAssignee(household, assigneeAccountId);
          Deadline resolved =
              deadline == null ? Deadline.none() : deadline.resolve(household.timezone());
          Task task =
              Task.create(
                  UUID.randomUUID(),
                  householdId,
                  null,
                  title,
                  description,
                  priority == null ? TaskPriority.NORMAL : priority,
                  resolved,
                  assignee);
          store.insert(task);
          return view(household, task);
        });
  }

  /** Explicit assignment preserves the distinction between omission and unassigned. */
  public record AssignmentChoice(boolean specified, UUID accountId) {
    public AssignmentChoice {
      if (!specified && accountId != null) {
        throw new IllegalArgumentException("Omitted assignment cannot contain an account");
      }
    }

    public static AssignmentChoice omitted() {
      return new AssignmentChoice(false, null);
    }

    public static AssignmentChoice selected(UUID accountId) {
      return new AssignmentChoice(true, accountId);
    }
  }

  @Transactional
  public TaskView createLinked(
      UUID householdId,
      UUID responsibilityId,
      String title,
      String description,
      TaskPriority priority,
      DeadlineInput deadline,
      AssignmentChoice assignment) {
    Objects.requireNonNull(householdId, "householdId");
    Objects.requireNonNull(responsibilityId, "responsibilityId");
    Objects.requireNonNull(assignment, "assignment");
    return households.withLockedHousehold(
        householdId,
        household -> {
          var snapshot =
              responsibilities
                  .lockForTask(householdId, responsibilityId)
                  .orElseThrow(NotFound::new);
          if (snapshot.status() != ResponsibilityTaskAccess.Status.ACTIVE
              || snapshot.mode() != ResponsibilityTaskAccess.Mode.INDIVIDUAL
              || snapshot.ownerMembershipId() == null
              || !household.isActiveMembership(snapshot.ownerMembershipId())) {
            throw new InvalidState();
          }
          UUID assignee =
              assignment.specified()
                  ? resolveAssignee(household, assignment.accountId())
                  : snapshot.ownerMembershipId();
          Deadline resolved =
              deadline == null ? Deadline.none() : deadline.resolve(household.timezone());
          Task task =
              Task.create(
                  UUID.randomUUID(),
                  householdId,
                  responsibilityId,
                  title,
                  description,
                  priority == null ? TaskPriority.NORMAL : priority,
                  resolved,
                  assignee);
          store.insert(task);
          return view(household, task);
        });
  }

  @Transactional
  public TaskView get(UUID householdId, UUID taskId) {
    Objects.requireNonNull(householdId, "householdId");
    Objects.requireNonNull(taskId, "taskId");
    return households.withLockedHousehold(
        householdId,
        household -> view(household, store.lock(householdId, taskId).orElseThrow(NotFound::new)));
  }

  /** A supplied null clears nullable fields; an omitted field retains its previous value. */
  public record Field<T>(boolean specified, T value) {
    public Field {
      if (!specified && value != null) {
        throw new IllegalArgumentException("Omitted field cannot have a value");
      }
    }

    public static <T> Field<T> omitted() {
      return new Field<>(false, null);
    }

    public static <T> Field<T> supplied(T value) {
      return new Field<>(true, value);
    }
  }

  public record EditCommand(
      long version,
      Field<String> title,
      Field<String> description,
      Field<TaskPriority> priority,
      Field<DeadlineInput> deadline,
      Field<UUID> assigneeAccountId) {
    public EditCommand {
      if (version < 0) {
        throw new IllegalArgumentException("Version must be nonnegative");
      }
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(description, "description");
      Objects.requireNonNull(priority, "priority");
      Objects.requireNonNull(deadline, "deadline");
      Objects.requireNonNull(assigneeAccountId, "assigneeAccountId");
      if (!(title.specified()
          || description.specified()
          || priority.specified()
          || deadline.specified()
          || assigneeAccountId.specified())) {
        throw new IllegalArgumentException("At least one editable field is required");
      }
      if ((title.specified() && title.value() == null)
          || (priority.specified() && priority.value() == null)
          || (deadline.specified() && deadline.value() == null)) {
        throw new IllegalArgumentException("Title, priority and deadline cannot be null");
      }
    }
  }

  @Transactional
  public TaskView edit(UUID householdId, UUID taskId, EditCommand command) {
    Objects.requireNonNull(householdId, "householdId");
    Objects.requireNonNull(taskId, "taskId");
    Objects.requireNonNull(command, "command");
    return households.withLockedHousehold(
        householdId,
        household -> {
          Task current = store.lock(householdId, taskId).orElseThrow(NotFound::new);
          if (current.status() != TaskStatus.OPEN) {
            throw new InvalidState();
          }
          if (current.version() != command.version()) {
            throw new TaskVersionConflictException("Task version conflict");
          }
          EnumSet<EditedField> fields = EnumSet.noneOf(EditedField.class);
          if (command.title().specified()) {
            fields.add(EditedField.TITLE);
          }
          if (command.description().specified()) {
            fields.add(EditedField.DESCRIPTION);
          }
          if (command.priority().specified()) {
            fields.add(EditedField.PRIORITY);
          }
          if (command.deadline().specified()) {
            fields.add(EditedField.DEADLINE);
          }
          if (command.assigneeAccountId().specified()) {
            fields.add(EditedField.ASSIGNMENT);
          }
          Task updated =
              current.edit(
                  command.title().specified() ? command.title().value() : current.title(),
                  command.description().specified()
                      ? command.description().value()
                      : current.description(),
                  command.priority().specified() ? command.priority().value() : current.priority(),
                  command.deadline().specified()
                      ? command.deadline().value().resolve(household.timezone())
                      : current.deadline(),
                  command.assigneeAccountId().specified()
                      ? resolveAssignee(household, command.assigneeAccountId().value())
                      : current.assigneeMembershipId());
          EditFact fact =
              new EditFact(
                  UUID.randomUUID(),
                  taskId,
                  householdId,
                  household.actorAccountId(),
                  clock.instant(),
                  current.version(),
                  updated.version(),
                  fields);
          if (!store.update(updated, current.version(), fact)) {
            throw new TaskVersionConflictException("Task version conflict");
          }
          return view(household, updated);
        });
  }

  private UUID resolveAssignee(LockedHousehold household, UUID accountId) {
    return accountId == null
        ? null
        : household.activeMembershipId(accountId).orElseThrow(InvalidAssignee::new);
  }

  private TaskView view(LockedHousehold household, Task task) {
    UUID account =
        task.assigneeMembershipId() == null
            ? null
            : household.historicalAccountId(task.assigneeMembershipId()).orElseThrow(NotFound::new);
    return new TaskView(task, account, task.isOverdue(clock.instant()));
  }

  public record TaskView(Task task, UUID assigneeAccountId, boolean overdue) {}

  /** Unresolved input is converted under the locked household's authoritative zone. */
  public sealed interface DeadlineInput
      permits NoDeadline, DateDeadline, InstantDeadline, LocalDeadline {
    Deadline resolve(String householdZone);
  }

  public record NoDeadline() implements DeadlineInput {
    @Override
    public Deadline resolve(String householdZone) {
      return Deadline.none();
    }
  }

  public record DateDeadline(LocalDate date) implements DeadlineInput {
    @Override
    public Deadline resolve(String householdZone) {
      return Deadline.date(date, householdZone);
    }
  }

  public record InstantDeadline(Instant instant) implements DeadlineInput {
    @Override
    public Deadline resolve(String householdZone) {
      return Deadline.dateTime(instant);
    }
  }

  public record LocalDeadline(LocalDateTime local, Optional<Deadline.Occurrence> occurrence)
      implements DeadlineInput {
    public LocalDeadline {
      Objects.requireNonNull(occurrence, "occurrence");
    }

    @Override
    public Deadline resolve(String householdZone) {
      return Deadline.fromLocalDateTime(local, householdZone, occurrence);
    }
  }

  public static final class InvalidAssignee extends RuntimeException {}

  public static final class NotFound extends RuntimeException {}

  public static final class InvalidState extends RuntimeException {}
}
