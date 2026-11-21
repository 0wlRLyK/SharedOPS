package dev.sharedops.household.api;

import dev.sharedops.household.application.CreateHouseholdService;
import dev.sharedops.household.application.GetHouseholdService;
import dev.sharedops.household.application.HouseholdDraft;
import dev.sharedops.household.application.HouseholdSnapshot;
import dev.sharedops.household.application.InvalidHouseholdException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HouseholdController {
  private final CreateHouseholdService service;
  private final GetHouseholdService getService;

  public HouseholdController(CreateHouseholdService service, GetHouseholdService getService) {
    this.service = service;
    this.getService = getService;
  }

  @PostMapping("/api/v1/households")
  public ResponseEntity<HouseholdSnapshot> create(@RequestBody CreateRequest request) {
    HouseholdDraft draft =
        request == null
            ? null
            : new HouseholdDraft(request.name, request.icon, request.currency, request.timezone);
    var result = service.create(draft);
    return ResponseEntity.created(URI.create("/api/v1/households/" + result.id())).body(result);
  }

  @GetMapping("/api/v1/households/{householdId}")
  public HouseholdSnapshot get(@PathVariable("householdId") String householdId) {
    UUID id;
    try {
      id = UUID.fromString(householdId);
      if (!id.toString().equalsIgnoreCase(householdId)) {
        throw new IllegalArgumentException();
      }
    } catch (IllegalArgumentException ex) {
      throw new InvalidHouseholdException();
    }
    return getService.get(id);
  }

  public static class CreateRequest {
    public String name;
    public String icon;
    public String currency;
    public String timezone = "Europe/Berlin";
  }
}
