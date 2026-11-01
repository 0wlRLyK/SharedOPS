package dev.sharedops.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FlywayChecksumIT {
  @Container
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.6-bookworm"));

  @Test
  void detectsModifiedMigrationChecksum() {
    var flywayA =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/checksum/original")
            .load();
    var flywayB =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/checksum/modified")
            .load();
    var migration = flywayA.migrate();
    var result = flywayB.validateWithResult();
    assertThat(result.invalidMigrations)
        .extracting(m -> m.errorDetails.errorCode.toString())
        .contains("CHECKSUM_MISMATCH");
    assertThat(result.validationSuccessful).isFalse();
    assertThat(migration.migrationsExecuted).isEqualTo(1);
  }
}
