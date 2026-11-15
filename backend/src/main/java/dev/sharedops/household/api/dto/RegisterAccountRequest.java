package dev.sharedops.household.api.dto;

import dev.sharedops.household.api.validation.PasswordBytes;
import dev.sharedops.household.domain.utils.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterAccountRequest(
    @NotBlank @Email @Size(max = 254) String email, @NotBlank @PasswordBytes String password) {
  public RegisterAccountRequest {
    if (email != null) {
      email = EmailNormalizer.normalize(email);
    }
  }
}
