package dev.sharedops.household.application;

import java.util.Optional;
import java.util.UUID;

public interface CurrentAccount {
  Optional<UUID> authenticatedAccountId();
}
