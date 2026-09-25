package de.dtfb.sportshub.backend.roster;

import de.dtfb.sportshub.backend.category.IneligibilityReason;

/** One player refused by the league category's eligibility profile, as reported in a 409 body. */
public record IneligiblePlayer(String playerId, String name, IneligibilityReason reason) {
}
