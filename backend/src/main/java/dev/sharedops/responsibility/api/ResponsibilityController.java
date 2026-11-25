package dev.sharedops.responsibility.api;

import dev.sharedops.responsibility.application.ResponsibilityResponses;
import dev.sharedops.responsibility.application.ResponsibilityService;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@Transactional
@RequestMapping("/api/v1/households/{householdId}/responsibilities")
public class ResponsibilityController {
  private final ResponsibilityService service;
  private final ResponsibilityResponses responses;

  public ResponsibilityController(
      ResponsibilityService service, ResponsibilityResponses responses) {
    this.service = service;
    this.responses = responses;
  }

  @PostMapping
  public ResponseEntity<ResponsibilityResponses.View> create(
      @PathVariable UUID householdId, @RequestBody JsonNode body) {
    fields(body, Set.of("title", "description", "mode", "ownerAccountId"));
    String mode = requiredString(body, "mode");
    if (!mode.equals("INDIVIDUAL")) {
      throw new Invalid("VALIDATION_ERROR");
    }
    var v =
        responses.project(
            service.create(
                householdId,
                requiredString(body, "title"),
                description(body),
                id(body, "ownerAccountId", true)));
    return ResponseEntity.created(
            URI.create("/api/v1/households/" + householdId + "/responsibilities/" + v.id()))
        .body(v);
  }

  @GetMapping
  public List<ResponsibilityResponses.View> list(
      @PathVariable UUID householdId, @RequestParam String view) {
    if (!view.equals("current") && !view.equals("archived")) {
      throw new Invalid("INVALID_VALUE");
    }
    return responses.projectList(householdId, service.list(householdId, view.equals("archived")));
  }

  @GetMapping("/{responsibilityId}")
  public ResponsibilityResponses.View get(
      @PathVariable UUID householdId, @PathVariable UUID responsibilityId) {
    return responses.project(service.get(householdId, responsibilityId));
  }

  @PutMapping("/{responsibilityId}/details")
  public ResponsibilityResponses.View edit(
      @PathVariable UUID householdId,
      @PathVariable UUID responsibilityId,
      @RequestBody JsonNode body) {
    fields(body, Set.of("version", "title", "description"));
    return responses.project(
        service.edit(
            householdId,
            responsibilityId,
            version(body),
            requiredString(body, "title"),
            description(body)));
  }

  @PostMapping("/{responsibilityId}/transfer")
  public ResponsibilityResponses.View transfer(
      @PathVariable UUID householdId,
      @PathVariable UUID responsibilityId,
      @RequestBody JsonNode body) {
    fields(body, Set.of("version", "ownerAccountId"));
    return responses.project(
        service.transfer(
            householdId, responsibilityId, version(body), id(body, "ownerAccountId", true)));
  }

  @PostMapping("/{responsibilityId}/{action:pause|resume|archive|restore}")
  public ResponsibilityResponses.View transition(
      @PathVariable UUID householdId,
      @PathVariable UUID responsibilityId,
      @PathVariable String action,
      @RequestBody JsonNode body) {
    fields(
        body, action.equals("restore") ? Set.of("version", "ownerAccountId") : Set.of("version"));
    long v = version(body);
    var result =
        switch (action) {
          case "pause" -> service.pause(householdId, responsibilityId, v);
          case "resume" -> service.resume(householdId, responsibilityId, v);
          case "archive" -> service.archive(householdId, responsibilityId, v);
          default ->
              service.restore(householdId, responsibilityId, v, id(body, "ownerAccountId", false));
        };
    return responses.project(result);
  }

  private static void fields(JsonNode body, Set<String> allowed) {
    if (body == null || !body.isObject()) {
      throw new Invalid("INVALID_VALUE");
    }
    body.propertyNames()
        .forEach(
            n -> {
              if (!allowed.contains(n)) {
                throw new Invalid("VALIDATION_ERROR");
              }
            });
  }

  private static String requiredString(JsonNode b, String key) {
    JsonNode n = b.get(key);
    if (n == null || n.isNull()) {
      throw new Invalid("VALIDATION_ERROR");
    }
    if (!n.isString()) {
      throw new Invalid("INVALID_VALUE");
    }
    String s = n.asString();
    if (s.isBlank()) {
      throw new Invalid("VALIDATION_ERROR");
    }
    return s;
  }

  private static String description(JsonNode b) {
    JsonNode n = b.get("description");
    if (n == null || n.isNull()) {
      return null;
    }
    if (!n.isString()) {
      throw new Invalid("INVALID_VALUE");
    }
    return n.asString();
  }

  private static UUID id(JsonNode b, String key, boolean required) {
    JsonNode n = b.get(key);
    if (n == null) {
      if (required) {
        throw new Invalid("VALIDATION_ERROR");
      }
      return null;
    }
    if (n.isNull()) {
      throw new Invalid("VALIDATION_ERROR");
    }
    if (!n.isString()) {
      throw new Invalid("INVALID_VALUE");
    }
    try {
      String raw = n.asString();
      UUID u = UUID.fromString(raw);
      if (!u.toString().equalsIgnoreCase(raw)) {
        throw new IllegalArgumentException();
      }
      return u;
    } catch (IllegalArgumentException e) {
      throw new Invalid("INVALID_VALUE");
    }
  }

  private static long version(JsonNode b) {
    JsonNode n = b.get("version");
    if (n == null || n.isNull()) {
      throw new Invalid("VALIDATION_ERROR");
    }
    if (!n.isIntegralNumber() || !n.canConvertToLong() || n.asLong() < 0) {
      throw new Invalid("INVALID_VALUE");
    }
    return n.asLong();
  }

  static final class Invalid extends RuntimeException {
    final String code;

    Invalid(String code) {
      this.code = code;
    }
  }
}
