package dev.sharedops.task.api;

import dev.sharedops.task.application.TaskService;
import dev.sharedops.task.application.TaskService.AssignmentChoice;
import dev.sharedops.task.application.TaskService.DateDeadline;
import dev.sharedops.task.application.TaskService.DeadlineInput;
import dev.sharedops.task.application.TaskService.EditCommand;
import dev.sharedops.task.application.TaskService.Field;
import dev.sharedops.task.application.TaskService.InstantDeadline;
import dev.sharedops.task.application.TaskService.LocalDeadline;
import dev.sharedops.task.application.TaskService.NoDeadline;
import dev.sharedops.task.domain.Deadline;
import dev.sharedops.task.domain.TaskPriority;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/households/{householdId}/tasks")
public class TaskController {
  private static final JsonMapper JSON =
      JsonMapper.builder(
              JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();
  private final TaskService service;

  public TaskController(TaskService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<View> create(@PathVariable String householdId, @RequestBody String raw) {
    UUID household = uuid(householdId);
    JsonNode body = parse(raw);
    fields(
        body,
        Set.of(
            "title",
            "description",
            "responsibilityId",
            "priority",
            "assigneeAccountId",
            "deadline"));
    String title = requiredString(body, "title");
    String description = nullableString(body, "description");
    UUID responsibility = nullableId(body, "responsibilityId");
    UUID assignee = nullableId(body, "assigneeAccountId");
    TaskPriority priority = body.has("priority") ? priority(body.get("priority")) : null;
    DeadlineInput deadline = body.has("deadline") ? deadline(body.get("deadline")) : null;
    TaskService.TaskView result =
        responsibility == null
            ? service.createStandalone(household, title, description, priority, deadline, assignee)
            : service.createLinked(
                household,
                responsibility,
                title,
                description,
                priority,
                deadline,
                body.has("assigneeAccountId")
                    ? AssignmentChoice.selected(assignee)
                    : AssignmentChoice.omitted());
    View view = view(result);
    return ResponseEntity.created(
            URI.create("/api/v1/households/" + household + "/tasks/" + view.id()))
        .body(view);
  }

  @GetMapping("/{taskId}")
  public View get(@PathVariable String householdId, @PathVariable String taskId) {
    return view(service.get(uuid(householdId), uuid(taskId)));
  }

  @PatchMapping("/{taskId}")
  public View edit(
      @PathVariable String householdId, @PathVariable String taskId, @RequestBody String raw) {
    JsonNode body = parse(raw);
    fields(
        body,
        Set.of("version", "title", "description", "priority", "deadline", "assigneeAccountId"));
    JsonNode version = body.get("version");
    if (version == null
        || !version.isIntegralNumber()
        || !version.canConvertToLong()
        || version.asLong() < 0) {
      throw new Invalid("INVALID_VALUE");
    }
    EditCommand command =
        new EditCommand(
            version.asLong(),
            body.has("title") ? Field.supplied(requiredString(body, "title")) : Field.omitted(),
            body.has("description")
                ? Field.supplied(nullableString(body, "description"))
                : Field.omitted(),
            body.has("priority") ? Field.supplied(priority(body.get("priority"))) : Field.omitted(),
            body.has("deadline") ? Field.supplied(deadline(body.get("deadline"))) : Field.omitted(),
            body.has("assigneeAccountId")
                ? Field.supplied(nullableId(body, "assigneeAccountId"))
                : Field.omitted());
    return view(service.edit(uuid(householdId), uuid(taskId), command));
  }

  private static JsonNode parse(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (RuntimeException e) {
      throw new Invalid("INVALID_VALUE");
    }
  }

  private static void fields(JsonNode body, Set<String> allowed) {
    if (body == null || !body.isObject()) {
      throw new Invalid("INVALID_VALUE");
    }
    body.propertyNames()
        .forEach(
            name -> {
              if (!allowed.contains(name)) {
                throw new Invalid("VALIDATION_ERROR");
              }
            });
  }

  private static String requiredString(JsonNode body, String key) {
    JsonNode n = body.get(key);
    if (n == null || n.isNull()) {
      throw new Invalid("VALIDATION_ERROR");
    }
    if (!n.isString()) {
      throw new Invalid("INVALID_VALUE");
    }
    String value = n.asString();
    if (value.isBlank()) {
      throw new Invalid("VALIDATION_ERROR");
    }
    return value;
  }

  private static String nullableString(JsonNode body, String key) {
    JsonNode n = body.get(key);
    if (n == null || n.isNull()) {
      return null;
    }
    if (!n.isString()) {
      throw new Invalid("INVALID_VALUE");
    }
    return n.asString();
  }

  private static UUID nullableId(JsonNode body, String key) {
    JsonNode n = body.get(key);
    if (n == null || n.isNull()) {
      return null;
    }
    if (!n.isString()) {
      throw new Invalid("INVALID_VALUE");
    }
    return uuid(n.asString());
  }

  private static UUID uuid(String raw) {
    try {
      UUID value = UUID.fromString(raw);
      if (!value.toString().equalsIgnoreCase(raw)) {
        throw new IllegalArgumentException();
      }
      return value;
    } catch (IllegalArgumentException e) {
      throw new Invalid("INVALID_VALUE");
    }
  }

  private static TaskPriority priority(JsonNode n) {
    if (n == null || !n.isString()) {
      throw new Invalid("INVALID_VALUE");
    }
    try {
      return TaskPriority.valueOf(n.asString());
    } catch (IllegalArgumentException e) {
      throw new Invalid("INVALID_VALUE");
    }
  }

  private static DeadlineInput deadline(JsonNode n) {
    fields(n, Set.of("kind", "date", "instant", "localDateTime", "occurrence"));
    String kind = requiredString(n, "kind");
    try {
      return switch (kind) {
        case "NONE" -> {
          fields(n, Set.of("kind"));
          yield new NoDeadline();
        }
        case "DATE" -> {
          fields(n, Set.of("kind", "date"));
          yield new DateDeadline(LocalDate.parse(requiredString(n, "date")));
        }
        case "DATE_TIME" -> {
          if (n.has("instant")) {
            fields(n, Set.of("kind", "instant"));
            String value = requiredString(n, "instant");
            if (!value.endsWith("Z")) {
              throw new Invalid("INVALID_VALUE");
            }
            yield new InstantDeadline(Instant.parse(value));
          }
          fields(n, Set.of("kind", "localDateTime", "occurrence"));
          LocalDateTime local = LocalDateTime.parse(requiredString(n, "localDateTime"));
          Optional<Deadline.Occurrence> occurrence =
              n.has("occurrence")
                  ? Optional.of(Deadline.Occurrence.valueOf(requiredString(n, "occurrence")))
                  : Optional.empty();
          yield new LocalDeadline(local, occurrence);
        }
        default -> throw new Invalid("INVALID_VALUE");
      };
    } catch (java.time.DateTimeException | IllegalArgumentException e) {
      throw new Invalid("INVALID_VALUE");
    }
  }

  private static View view(TaskService.TaskView value) {
    var t = value.task();
    Map<String, String> deadline =
        switch (t.deadline()) {
          case Deadline.None ignored -> Map.of("kind", "NONE");
          case Deadline.Date d ->
              Map.of(
                  "kind",
                  "DATE",
                  "date",
                  d.dueDate().toString(),
                  "zone",
                  d.householdZone().getId());
          case Deadline.DateTime d ->
              Map.of("kind", "DATE_TIME", "instant", d.instant().toString());
        };
    return new View(
        t.id(),
        t.householdId(),
        t.responsibilityId(),
        t.title(),
        t.description(),
        t.priority(),
        t.status(),
        t.version(),
        value.assigneeAccountId(),
        deadline,
        value.overdue());
  }

  record View(
      UUID id,
      UUID householdId,
      UUID responsibilityId,
      String title,
      String description,
      TaskPriority priority,
      dev.sharedops.task.domain.TaskStatus status,
      long version,
      UUID assigneeAccountId,
      Map<String, String> deadline,
      boolean overdue) {}

  static final class Invalid extends RuntimeException {
    final String code;

    Invalid(String code) {
      this.code = code;
    }
  }
}
