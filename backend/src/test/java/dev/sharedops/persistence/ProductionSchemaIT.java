package dev.sharedops.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sharedops.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProductionSchemaIT {
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired Flyway flyway;

  @Test
  void startsWithProductionMigrationWithoutSmokeTable() {
    assertThat(jdbcTemplate.queryForObject("select to_regclass('public.households')", String.class))
        .isEqualTo("households");
    assertThat(
            jdbcTemplate.queryForObject(
                "select to_regclass('public.persistence_smoke_record')", String.class))
        .isNull();
  }

  @Test
  void doesNotReapplyExistingMigration() {
    var result = flyway.validateWithResult();
    assertThat(result.validationSuccessful).isTrue();
    var migration = flyway.migrate();
    assertThat(migration.migrationsExecuted).isEqualTo(0);
  }
}
