package dev.sharedops.household.api;

import dev.sharedops.household.application.ApplicationServiceFixture;

public class AllowedApiToApplication {
  ApplicationServiceFixture fixture;

  public AllowedApiToApplication(ApplicationServiceFixture fixture) {
    this.fixture = fixture;
  }
}
