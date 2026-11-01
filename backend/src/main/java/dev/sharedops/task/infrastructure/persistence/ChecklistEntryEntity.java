package dev.sharedops.task.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "checklist_entries")
public class ChecklistEntryEntity {
  @Id
  @Column(name = "id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_id", nullable = false)
  private TaskEntity task;

  @Column(name = "text", nullable = false)
  private String text;

  @Column(name = "is_completed", nullable = false)
  private boolean isCompleted;

  @Column(name = "position", nullable = false)
  private int position;

  public ChecklistEntryEntity(
      UUID id, TaskEntity task, String text, boolean isCompleted, int position) {
    this.id = id;
    this.task = task;
    this.text = text;
    this.isCompleted = isCompleted;
    this.position = position;
  }

  public ChecklistEntryEntity() {}

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getId() {
    return id;
  }

  public TaskEntity getTask() {
    return task;
  }

  void setTask(TaskEntity task) {
    this.task = task;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public boolean isCompleted() {
    return isCompleted;
  }

  public void setCompleted(boolean completed) {
    isCompleted = completed;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }
}
