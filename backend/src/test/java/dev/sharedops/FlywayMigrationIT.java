package dev.sharedops;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FlywayMigrationIT {

  @Container
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-bookworm");

  @Test
  void appliesV1MigrationToEmptyPostgres() throws Exception {
    var flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/flyway-smoke")
            .load();

    var result = flyway.migrate();

    assertThat(result.migrationsExecuted).isEqualTo(1);

    try (var connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

      try (var statement = connection.createStatement();
          var resultSet =
              statement.executeQuery("SELECT to_regclass('public.flyway_smoke_marker')")) {

        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getString(1)).isEqualTo("flyway_smoke_marker");
      }
    }

    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }
}
