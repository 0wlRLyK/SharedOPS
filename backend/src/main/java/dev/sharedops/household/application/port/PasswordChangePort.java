package dev.sharedops.household.application.port;

import java.util.UUID;

public interface PasswordChangePort {
  void change(UUID accountId, String currentPassword, String newPassword);
}
