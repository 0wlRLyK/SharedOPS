package dev.sharedops.household.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "households")
public class HouseholdEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  public UUID getId() {
    return id;
  }
}
