package dev.sharedops.household.contract;

import org.springframework.modulith.NamedInterface;

/** Exported household responsibility access failure. */
@NamedInterface("responsibility")
public class ResponsibilityUnauthenticated extends RuntimeException {}
