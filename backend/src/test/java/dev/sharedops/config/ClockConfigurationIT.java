package dev.sharedops.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = {ClockConfiguration.class, TestClockConfiguration.class})
class ClockConfigurationIT {

  @Autowired Clock clock;

  @Test
  void usesFixedClockInTests() {
    assertThat(clock.instant()).isEqualTo(Instant.parse("2026-10-25T12:00:00Z"));
  }
}
