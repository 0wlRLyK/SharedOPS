package dev.sharedops.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.FieldError;

class PaginationPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void bindsValidPaginationProperties() {
    contextRunner
        .withPropertyValues(
            "sharedops.api.pagination.default-page-size=20",
            "sharedops.api.pagination.max-page-size=100")
        .run(
            context -> {
              PaginationProperties properties = context.getBean(PaginationProperties.class);
              assertThat(properties.defaultPageSize()).isEqualTo(20);
              assertThat(properties.maxPageSize()).isEqualTo(100);
            });
  }

  @Test
  void rejectsNonPositiveMaxPageSize() {
    contextRunner
        .withPropertyValues(
            "sharedops.api.pagination.default-page-size=20",
            "sharedops.api.pagination.max-page-size=0")
        .run(
            context -> {
              assertThat(context.getStartupFailure())
                  .isInstanceOf(ConfigurationPropertiesBindException.class);

              Throwable rootCause = context.getStartupFailure();

              while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
              }

              assertThat(rootCause).isInstanceOf(BindValidationException.class);

              BindValidationException validationException = (BindValidationException) rootCause;

              var errors = validationException.getValidationErrors().getAllErrors();
              assertThat(errors)
                  .anySatisfy(
                      error -> {
                        assertThat(error).isInstanceOf(FieldError.class);

                        FieldError fieldError = (FieldError) error;
                        assertThat(fieldError.getField()).isEqualTo("maxPageSize");
                        assertThat(fieldError.getCode()).isEqualTo("Min");
                        assertThat(fieldError.getRejectedValue()).isEqualTo(0);
                      });
            });
  }

  @Test
  void rejectsMissingMaxPageSize() {
    contextRunner
        .withPropertyValues("sharedops.api.pagination.default-page-size=20")
        .run(
            context -> {
              assertThat(context.getStartupFailure())
                  .isInstanceOf(ConfigurationPropertiesBindException.class);

              Throwable rootCause = context.getStartupFailure();

              while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
              }

              assertThat(rootCause).isInstanceOf(BindValidationException.class);

              BindValidationException validationException = (BindValidationException) rootCause;

              var errors = validationException.getValidationErrors().getAllErrors();
              assertThat(errors)
                  .anySatisfy(
                      error -> {
                        assertThat(error).isInstanceOf(FieldError.class);

                        FieldError fieldError = (FieldError) error;
                        assertThat(fieldError.getField()).isEqualTo("maxPageSize");
                        assertThat(fieldError.getCode()).isEqualTo("NotNull");
                        assertThat(fieldError.getRejectedValue()).isNull();
                      });
            });
  }

  @Test
  void rejectsInvalidDefaultPageSize() {
    contextRunner
        .withPropertyValues(
            "sharedops.api.pagination.default-page-size=-20",
            "sharedops.api.pagination.max-page-size=1")
        .run(
            context -> {
              assertThat(context.getStartupFailure())
                  .isInstanceOf(ConfigurationPropertiesBindException.class);
              Throwable rootCause = context.getStartupFailure();

              while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
              }

              assertThat(rootCause).isInstanceOf(BindValidationException.class);

              BindValidationException validationException = (BindValidationException) rootCause;

              var errors = validationException.getValidationErrors().getAllErrors();
              assertThat(errors)
                  .anySatisfy(
                      error -> {
                        assertThat(error).isInstanceOf(FieldError.class);

                        FieldError fieldError = (FieldError) error;
                        assertThat(fieldError.getField()).isEqualTo("defaultPageSize");
                        assertThat(fieldError.getCode()).isEqualTo("Min");
                        assertThat(fieldError.getRejectedValue()).isEqualTo(-20);
                      });
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(PaginationProperties.class)
  static class TestConfig {}
}
