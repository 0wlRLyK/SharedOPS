package dev.sharedops.household.application;

import dev.sharedops.household.application.port.PasswordChangePort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChangePasswordService {
  private final PasswordChangePort port;

  public ChangePasswordService(PasswordChangePort port) {
    this.port = port;
  }

  public void change(UUID accountId, String currentPassword, String newPassword) {
    port.change(accountId, currentPassword, newPassword);
  }
}
