package dev.sharedops.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SharedopsBackendTestProfileIT {

  @Autowired Flyway flyway;

  @Test
  void contextLoadsWithProductionMigrations() {
    assertThat(flyway.info().applied())
        .extracting(migration -> migration.getScript())
        .contains("V1__create_core_persistence.sql")
        .doesNotContain("V1__create_smoke_marker.sql", "V1__create_smoke_record.sql");
  }
}
