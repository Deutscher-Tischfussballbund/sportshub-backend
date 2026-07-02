package de.dtfb.sportshub.backend.teamparticipation;

/**
 * Lifecycle of a team's whole roster for one participation (L2). The team edits in
 * {@code DRAFT}, {@code submit} locks it to {@code SUBMITTED}, an admin {@code confirm}s to
 * {@code CONFIRMED}; an admin may {@code reopen} back to {@code DRAFT}. It describes the roster's
 * completeness/approval as a whole — individual players are simply present or removed
 * (see {@code RosterEntry.removedAt}).
 */
public enum RosterStatus {
    DRAFT,
    SUBMITTED,
    CONFIRMED
}
