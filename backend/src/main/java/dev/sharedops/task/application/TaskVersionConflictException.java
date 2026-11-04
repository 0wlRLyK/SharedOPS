package dev.sharedops.task.application;

public class TaskVersionConflictException extends RuntimeException {
  public TaskVersionConflictException(String message) {
    super(message);
  }
}
