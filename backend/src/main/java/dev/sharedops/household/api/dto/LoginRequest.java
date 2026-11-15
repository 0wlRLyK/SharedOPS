package dev.sharedops.household.api.dto;

import dev.sharedops.household.domain.utils.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Email @Size(max = 254) String email, @NotBlank String password, Boolean rememberMe) {
  public LoginRequest {
    if (email != null) {
      email = EmailNormalizer.normalize(email);
    }
  }
}
