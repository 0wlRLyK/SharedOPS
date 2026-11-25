package dev.sharedops.household.application;

import java.util.Optional;
import java.util.UUID;

public interface CurrentContextStore {
  Optional<CurrentContext> findForAccount(UUID accountId);
}
