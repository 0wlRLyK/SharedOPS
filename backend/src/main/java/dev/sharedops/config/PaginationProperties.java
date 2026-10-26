package dev.sharedops.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "sharedops.api.pagination")
@Validated
public record PaginationProperties(
    @NotNull @Min(1) Integer defaultPageSize, @NotNull @Min(1) Integer maxPageSize) {}
