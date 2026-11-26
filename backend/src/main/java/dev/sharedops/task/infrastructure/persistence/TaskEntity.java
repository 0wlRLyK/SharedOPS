package dev.sharedops.task.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "household_id", nullable = false)
  private UUID householdId;

  @Column(name = "responsibility_id", nullable = true)
  private UUID responsibilityId;

  @Column(name = "title", nullable = false, columnDefinition = "text")
  private String title;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "priority", nullable = false, columnDefinition = "text")
  private String priority = "NORMAL";

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status = "OPEN";

  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "assignee_membership_id")
  private UUID assigneeMembershipId;

  @Column(name = "deadline_kind", nullable = false, columnDefinition = "text")
  private String deadlineKind = "NONE";

  @Column(name = "due_date_iso", columnDefinition = "text")
  private String dueDateIso;

  @Column(name = "due_zone", columnDefinition = "text")
  private String dueZone;

  @Column(name = "due_epoch_second")
  private Long dueEpochSecond;

  @Column(name = "due_nano")
  private Integer dueNano;

  @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ChecklistEntryEntity> checklistEntries = new ArrayList<>();

  public TaskEntity(UUID householdId, String title) {
    this.householdId = householdId;
    this.title = title;
  }

  public TaskEntity() {}

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
