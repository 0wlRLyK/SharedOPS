package dev.sharedops.architecture.fixtures.domain;

import org.springframework.context.ApplicationContext;

public record ForbiddenDomainDependency(ApplicationContext context) {}
