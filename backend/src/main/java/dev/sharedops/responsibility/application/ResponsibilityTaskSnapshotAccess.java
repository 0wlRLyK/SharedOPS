package dev.sharedops.responsibility.application;

import dev.sharedops.responsibility.contract.ResponsibilityTaskAccess;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Resolves a locked snapshot after the caller has authorized and locked the household. */
@Service
public class ResponsibilityTaskSnapshotAccess implements ResponsibilityTaskAccess {
  private final ResponsibilityStore store;

  public ResponsibilityTaskSnapshotAccess(ResponsibilityStore store) {
    this.store = store;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<Snapshot> lockForTask(UUID householdId, UUID responsibilityId) {
    Objects.requireNonNull(householdId, "householdId");
    Objects.requireNonNull(responsibilityId, "responsibilityId");
    return store
        .lock(householdId, responsibilityId)
        .map(
            responsibility ->
                new Snapshot(
                    responsibility.ownerMembershipId(),
                    Status.valueOf(responsibility.status().name()),
                    Mode.valueOf(responsibility.mode().name())));
  }
}
