package dev.sharedops.household.application;

import dev.sharedops.household.domain.AllowedDomainDependency;

public class ApplicationServiceFixture {
  AllowedDomainDependency fixture;

  public ApplicationServiceFixture(AllowedDomainDependency fixture) {
    this.fixture = fixture;
  }
}
