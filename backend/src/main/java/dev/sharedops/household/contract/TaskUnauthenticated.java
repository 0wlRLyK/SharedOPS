package dev.sharedops.household.contract;

import org.springframework.modulith.NamedInterface;

/** Exported household task access failure for an unauthenticated actor. */
@NamedInterface("task")
public class TaskUnauthenticated extends RuntimeException {}
