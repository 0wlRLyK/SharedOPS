package dev.sharedops.household.api;

import dev.sharedops.household.api.validation.PasswordBytes;
import dev.sharedops.household.application.AuthenticatedAccount;
import dev.sharedops.household.application.ChangePasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordController {
  private final ChangePasswordService service;

  public PasswordController(ChangePasswordService service) {
    this.service = service;
  }

  public record PasswordRequest(
      @NotBlank String currentPassword, @NotBlank @PasswordBytes String newPassword) {}

  @PostMapping("/api/v1/auth/password")
  public ResponseEntity<Void> change(
      @Valid @RequestBody PasswordRequest body, Authentication auth, HttpServletRequest request) {
    var principal = (AuthenticatedAccount) auth.getPrincipal();
    service.change(principal.accountId(), body.currentPassword(), body.newPassword());
    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }
}
