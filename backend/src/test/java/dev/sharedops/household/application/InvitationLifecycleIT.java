package dev.sharedops.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.household.domain.InvitationToken;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

/** Exercises real service proxies and production migrations without test transaction rollback. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, InvitationLifecycleIT.TimeConfiguration.class})
class InvitationLifecycleIT {
  private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
  @Autowired InvitationService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired MutableClock clock;
  @MockitoSpyBean InvitationStore invitations;

  private final UUID owner = UUID.randomUUID();
  private final UUID joiner = UUID.randomUUID();
  private final UUID outsider = UUID.randomUUID();
  private final UUID house = UUID.randomUUID();
  private final UUID foreignHouse = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    clock.now = START;
    for (UUID id : new UUID[] {owner, joiner, outsider}) {
      jdbc.update(
          "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,?)",
          id,
          id + "@example.com",
          "hash");
    }
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'A', 'EUR', 'Europe/Berlin'), (?, 'B', 'EUR', 'Europe/Berlin')",
        house,
        foreignHouse);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1), (?,?,?,true,1)",
        UUID.randomUUID(),
        owner,
        house,
        UUID.randomUUID(),
        outsider,
        foreignHouse);
    authenticate(owner);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    reset(invitations);
    jdbc.update(
        "DELETE FROM household_invitations WHERE household_id IN (?,?)", house, foreignHouse);
    jdbc.update(
        "DELETE FROM household_memberships WHERE household_id IN (?,?)", house, foreignHouse);
    jdbc.update("DELETE FROM households WHERE id IN (?,?)", house, foreignHouse);
    jdbc.update("DELETE FROM accounts WHERE id IN (?,?,?)", owner, joiner, outsider);
  }

  @Test
  void createsDigestOnlyAndAcceptsOnceJustBeforeDeadlineWithoutLoggingSecret() {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    root.addAppender(appender);
    try {
      IssuedInvitation issued = service.create(house);
      assertThat(issued.expiresAt()).isEqualTo(START.plusSeconds(86400));
      byte[] stored =
          jdbc.queryForObject(
              "SELECT token_hash FROM household_invitations WHERE id=?", byte[].class, issued.id());
      assertThat(stored).isEqualTo(InvitationToken.parse(issued.token()).digest());
      assertThat(new String(stored, java.nio.charset.StandardCharsets.UTF_8))
          .doesNotContain(issued.token());
      clock.now = issued.expiresAt().minusNanos(1000);
      authenticate(joiner);
      assertThat(service.accept(issued.token()).id()).isEqualTo(house);
      assertMembership(joiner, 1);
      assertTerminal(issued.id(), true, false);
      assertThatThrownBy(() -> service.accept(issued.token()))
          .isInstanceOf(InvalidInvitationException.class);
      assertMembership(joiner, 1);
      assertTerminal(issued.id(), true, false);
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .noneMatch(
              message ->
                  message.contains(issued.token())
                      || message.contains("/invitations/" + issued.token()));
    } finally {
      root.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void submicrosecondCreationReturnsCommittedDeadlineAndEnforcesItsBoundary() {
    clock.now = START.plusNanos(123_456_789);
    Instant persistedCreation = START.plusNanos(123_456_000);
    IssuedInvitation expiresAtBoundary = service.create(house);
    IssuedInvitation acceptedBeforeBoundary = service.create(house);

    Instant storedCreation =
        jdbc.queryForObject(
            "SELECT created_at FROM household_invitations WHERE id=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(),
            expiresAtBoundary.id());
    Instant storedExpiry =
        jdbc.queryForObject(
            "SELECT expires_at FROM household_invitations WHERE id=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(),
            expiresAtBoundary.id());
    assertThat(storedCreation).isEqualTo(persistedCreation);
    assertThat(storedExpiry).isEqualTo(persistedCreation.plusSeconds(86400));
    assertThat(expiresAtBoundary.expiresAt()).isEqualTo(storedExpiry);
    assertThat(acceptedBeforeBoundary.expiresAt()).isEqualTo(storedExpiry);

    authenticate(joiner);
    clock.now = storedExpiry;
    assertThatThrownBy(() -> service.accept(expiresAtBoundary.token()))
        .isInstanceOf(InvalidInvitationException.class);
    assertMembership(joiner, 0);
    assertTerminal(expiresAtBoundary.id(), false, false);

    // One nanosecond before the deadline must remain valid even though the JDBC
    // timestamp representation is limited to microsecond precision.
    clock.now = storedExpiry.minusNanos(1);
    assertThat(service.accept(acceptedBeforeBoundary.token()).id()).isEqualTo(house);
    assertMembership(joiner, 1);
    assertTerminal(acceptedBeforeBoundary.id(), true, false);
    Instant storedConsumption =
        jdbc.queryForObject(
            "SELECT consumed_at FROM household_invitations WHERE id=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(),
            acceptedBeforeBoundary.id());
    assertThat(storedConsumption).isEqualTo(storedExpiry.minusNanos(1000));
  }

  @Test
  void exactDeadlineRevokedUnknownAndConsumedInvitationsRejectWithoutWrites() {
    IssuedInvitation expired = service.create(house);
    IssuedInvitation revoked = service.create(house);
    service.revoke(house, revoked.id());
    clock.now = expired.expiresAt();
    authenticate(joiner);
    assertThatThrownBy(() -> service.accept(expired.token()))
        .isInstanceOf(InvalidInvitationException.class);
    assertThatThrownBy(() -> service.accept(revoked.token()))
        .isInstanceOf(InvalidInvitationException.class);
    assertThatThrownBy(() -> service.accept(InvitationToken.generate().reveal()))
        .isInstanceOf(InvalidInvitationException.class);
    assertMembership(joiner, 0);
    assertTerminal(expired.id(), false, false);
    assertTerminal(revoked.id(), false, true);
  }

  @Test
  void directApplicationAuthorizationAndExistingMembershipLeaveStateUnchanged() {
    IssuedInvitation issued = service.create(house);
    authenticate(outsider);
    assertThatThrownBy(() -> service.create(house)).isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> service.revoke(house, issued.id()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> service.revoke(UUID.randomUUID(), issued.id()))
        .isInstanceOf(HouseholdNotFoundException.class);
    assertThatThrownBy(() -> service.accept(issued.token()))
        .isInstanceOf(InvitationConflictException.class);
    assertMembership(outsider, 1);
    assertTerminal(issued.id(), false, false);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_invitations WHERE household_id=?",
                Integer.class,
                house))
        .isEqualTo(1);
  }

  @Test
  void realProxyRollsBackMembershipWhenConsumptionFailsAndBypassControlDetectsMissingTransaction()
      throws Exception {
    IssuedInvitation issued = service.create(house);
    authenticate(joiner);
    doThrow(new IllegalStateException("synthetic failure after membership insertion"))
        .when(invitations)
        .consume(any(), any());
    assertThatThrownBy(() -> service.accept(issued.token()))
        .hasStackTraceContaining("synthetic failure after membership insertion");
    assertMembership(joiner, 0);
    assertTerminal(issued.id(), false, false);

    // The unproxied target cannot wrap both persistence operations in one transaction.
    // The independently proxied membership store commits before the injected failure.
    InvitationService target = AopTestUtils.getUltimateTargetObject(service);
    assertThatThrownBy(() -> target.accept(issued.token()))
        .hasStackTraceContaining("synthetic failure after membership insertion");
    assertMembership(joiner, 1);
    assertTerminal(issued.id(), false, false);
  }

  private void authenticate(UUID id) {
    var principal = new AccountPrincipal(id, id + "@example.com", "hash", 0);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }

  private void assertMembership(UUID account, int expected) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_memberships WHERE account_id=? AND active",
                Integer.class,
                account))
        .isEqualTo(expected);
  }

  private void assertTerminal(UUID id, boolean consumed, boolean revoked) {
    assertThat(
            jdbc.queryForObject(
                "SELECT consumed_at IS NOT NULL FROM household_invitations WHERE id=?",
                Boolean.class,
                id))
        .isEqualTo(consumed);
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM household_invitations WHERE id=?",
                Boolean.class,
                id))
        .isEqualTo(revoked);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TimeConfiguration {
    @Bean
    @Primary
    MutableClock invitationTestClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    volatile Instant now = START;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
