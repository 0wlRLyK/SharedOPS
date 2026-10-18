package dev.sharedops.smoke.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "persistence_smoke_record")
public class PersistenceSmokeRecord {

  @Id private UUID id;

  private String title;

  private LocalDate createdOn;

  protected PersistenceSmokeRecord() {}

  public PersistenceSmokeRecord(UUID id, String title, LocalDate createdOn) {
    this.id = id;
    this.title = title;
    this.createdOn = createdOn;
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public LocalDate getCreatedOn() {
    return createdOn;
  }
}
