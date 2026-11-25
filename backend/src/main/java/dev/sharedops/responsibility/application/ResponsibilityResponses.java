package dev.sharedops.responsibility.application;

import dev.sharedops.household.contract.HouseholdResponsibilityAccess;
import dev.sharedops.responsibility.domain.Responsibility;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResponsibilityResponses {
  private final HouseholdResponsibilityAccess households;

  public ResponsibilityResponses(HouseholdResponsibilityAccess households) {
    this.households = households;
  }

  @Transactional
  public View project(Responsibility value) {
    return households.withLockedHousehold(value.householdId(), h -> view(value, h));
  }

  @Transactional
  public List<View> projectList(UUID householdId, List<Responsibility> values) {
    return households.withLockedHousehold(
        householdId, h -> values.stream().map(v -> view(v, h)).toList());
  }

  private View view(Responsibility v, HouseholdResponsibilityAccess.LockedHousehold h) {
    UUID owner =
        h.historicalAccountId(v.ownerMembershipId()).orElseThrow(IllegalStateException::new);
    return new View(
        v.id(),
        v.householdId(),
        v.title(),
        v.description(),
        v.mode().name(),
        v.status().name(),
        owner,
        v.version());
  }

  public record View(
      UUID id,
      UUID householdId,
      String title,
      String description,
      String mode,
      String status,
      UUID ownerAccountId,
      long version) {}
}
