package dev.sharedops.task.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class TaskEntity {
  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "household_id", nullable = false)
  private UUID householdId;

  @Column(name = "responsibility_id", nullable = true)
  private UUID responsibilityId;

  @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ChecklistEntryEntity> checklistEntries = new ArrayList<>();

  public TaskEntity(UUID taskId, UUID householdId) {
    this.id = taskId;
    this.householdId = householdId;
  }

  public TaskEntity() {}

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getId() {
    return id;
  }

  public UUID getHouseholdId() {
    return householdId;
  }

  public void setHouseholdId(UUID householdId) {
    this.householdId = householdId;
  }

  public UUID getResponsibilityId() {
    return responsibilityId;
  }

  public void setResponsibilityId(UUID responsibilityId) {
    this.responsibilityId = responsibilityId;
  }

  public void addChecklistEntry(ChecklistEntryEntity entry) {
    Objects.requireNonNull(entry, "entry");

    if (entry.getTask() != null && entry.getTask() != this) {
      throw new IllegalArgumentException("Entry belongs to another task");
    }

    if (!checklistEntries.contains(entry)) {
      checklistEntries.add(entry);
    }

    entry.setTask(this);
  }

  public void removeChecklistEntry(ChecklistEntryEntity entry) {
    if (checklistEntries.remove(entry)) {
      entry.setTask(null);
    }
  }

  public List<ChecklistEntryEntity> getChecklistEntries() {
    return this.checklistEntries;
  }
}
