package dev.sharedops.household.application;

/** A requested transition cannot be completed due to current membership or invitation state. */
public class InvitationConflictException extends RuntimeException {}
