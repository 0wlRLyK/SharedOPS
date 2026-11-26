package dev.sharedops.household.contract;

import org.springframework.modulith.NamedInterface;

/** Exported household task access failure for an inaccessible household. */
@NamedInterface("task")
public class TaskInaccessible extends RuntimeException {}
