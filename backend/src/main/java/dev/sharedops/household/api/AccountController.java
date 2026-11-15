package dev.sharedops.household.api;

import dev.sharedops.config.security.SessionPolicy;
import dev.sharedops.household.api.dto.AccountResponse;
import dev.sharedops.household.api.dto.CsrfTokenResponse;
import dev.sharedops.household.api.dto.LoginRequest;
import dev.sharedops.household.api.dto.RegisterAccountRequest;
import dev.sharedops.household.application.AuthenticatedAccount;
import dev.sharedops.household.application.RegisterAccountService;
import dev.sharedops.household.domain.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountController {

  private final Clock clock;
  private final RegisterAccountService accountService;
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

  AccountController(
      Clock clock,
      RegisterAccountService accountService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      SessionAuthenticationStrategy sessionAuthenticationStrategy) {
    this.clock = clock;
    this.accountService = accountService;
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
  }

  @PostMapping("/register")
  public ResponseEntity<AccountResponse> postRegister(
      @Valid @RequestBody RegisterAccountRequest accountRequest) {
    Account createdAccount =
        accountService.register(accountRequest.email(), accountRequest.password());
    AccountResponse accountResponse =
        new AccountResponse(createdAccount.getId().id(), createdAccount.getEmail());
    return new ResponseEntity<>(accountResponse, HttpStatus.CREATED);
  }

  @GetMapping("/csrf")
  public CsrfTokenResponse getCsrfToken(CsrfToken csrfToken) {
    return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
  }

  @PostMapping("/login")
  public ResponseEntity<AccountResponse> postLogin(
      @Valid @RequestBody LoginRequest loginRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    Authentication authentication =
        UsernamePasswordAuthenticationToken.unauthenticated(
            loginRequest.email(), loginRequest.password());
    Authentication authenticated = authenticationManager.authenticate(authentication);
    sessionAuthenticationStrategy.onAuthentication(authenticated, request, response);
    SessionPolicy.initialize(
        request.getSession(true), Boolean.TRUE.equals(loginRequest.rememberMe()), clock.instant());
    SecurityContext context = SecurityContextHolder.createEmptyContext();

    context.setAuthentication(authenticated);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    AuthenticatedAccount principal = (AuthenticatedAccount) authenticated.getPrincipal();
    AccountResponse accountResponse =
        new AccountResponse(principal.accountId(), principal.getUsername());

    return new ResponseEntity<>(accountResponse, HttpStatus.OK);
  }
}
