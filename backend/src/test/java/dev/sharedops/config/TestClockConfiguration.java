package dev.sharedops.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfiguration {

  @Primary
  @Bean
  public Clock testClock() {
    return Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);
  }
}
