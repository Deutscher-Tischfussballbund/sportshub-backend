package de.dtfb.sportshub.backend.category;

/**
 * Why a player fails a {@link Category}'s eligibility profile. Deliberately never carries the
 * player's stored gender or league side -- a team admin sees this reason too, and the divers
 * league side is admin-only (docs/18-player-gender.md).
 */
public enum IneligibilityReason {
    /** The player competes on the other side of the men's/women's split. */
    WRONG_SIDE,
    /** Birth year or gender unknown (an old imported record) -- complete the player first (docs/28). */
    INCOMPLETE
}
