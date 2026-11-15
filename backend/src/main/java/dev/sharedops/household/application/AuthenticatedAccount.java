package dev.sharedops.household.application;

import java.util.UUID;

/** Identity exposed to the API without coupling it to the security adapter. */
public interface AuthenticatedAccount {
  UUID accountId();

  String getUsername();
}
