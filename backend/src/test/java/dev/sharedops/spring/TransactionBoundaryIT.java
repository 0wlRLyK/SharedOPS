package dev.sharedops.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import jakarta.transaction.Transactional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringJUnitConfig(TransactionBoundaryIT.TestConfig.class)
class TransactionBoundaryIT {

  @Container
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.6-bookworm"));

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired TransactionProbeService service;

  @BeforeEach
  void prepareDatabase() {
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS transaction_probe ( id UUID PRIMARY KEY);");
    jdbcTemplate.execute("TRUNCATE TABLE transaction_probe;");
  }

  @Test
  void rollsBackOnRuntimeException() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> service.writeThenFail(id)).isInstanceOf(IllegalStateException.class);

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_probe WHERE id = ?", Integer.class, id);
    assertThat(count).isEqualTo(0);
    assertThat(AopUtils.isAopProxy(service)).isTrue();
  }

  @Test
  void rollsBackWhenOuterMethodIsTransactional() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> service.transactionalEntryPoint(id))
        .isInstanceOf(IllegalStateException.class);

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_probe WHERE id = ?", Integer.class, id);
    assertThat(count).isEqualTo(0);
    assertThat(AopUtils.isAopProxy(service)).isTrue();
  }

  @Test
  void selfInvoke() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(() -> service.selfInvokeThenFail(id))
        .isInstanceOf(IllegalStateException.class);

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transaction_probe WHERE id = ?", Integer.class, id);
    assertThat(count).isEqualTo(1);
  }

  @Service
  static class TransactionProbeService {

    private final JdbcTemplate jdbcTemplate;

    TransactionProbeService(JdbcTemplate jdbcTemplate) {
      this.jdbcTemplate = jdbcTemplate;
    }

    public void selfInvokeThenFail(UUID id) {
      this.writeThenFail(id);
    }

    @Transactional
    public void transactionalEntryPoint(UUID id) {
      this.writeThenFail(id);
    }

    @Transactional
    public void writeThenFail(UUID id) {
      // TODO: INSERT у таблицю transaction_probe
      jdbcTemplate.update("INSERT INTO transaction_probe (id) VALUES (?);", id);

      throw new IllegalStateException("Simulated failure");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @Import(TransactionProbeService.class)
  static class TestConfig {

    @Bean
    DataSource dataSource() {
      var dataSource = new DriverManagerDataSource();

      dataSource.setUrl(postgres.getJdbcUrl());
      dataSource.setUsername(postgres.getUsername());
      dataSource.setPassword(postgres.getPassword());

      return dataSource;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new JdbcTransactionManager(dataSource);
    }
  }
}
