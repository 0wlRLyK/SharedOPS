package dev.sharedops.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
    classes = PaginationConfigurationPrecedenceIT.TestConfig.class,
    properties = {"sharedops.api.pagination.max-page-size=25"})
class PaginationConfigurationPrecedenceIT {

  @Autowired PaginationProperties properties;

  @Test
  void testPropertyOverridesProfileConfiguration() {
    assertThat(properties.maxPageSize()).isEqualTo(25);
    assertThat(properties.defaultPageSize()).isEqualTo(20);
  }

  @SpringBootConfiguration
  @EnableConfigurationProperties(PaginationProperties.class)
  static class TestConfig {}
}
