package de.dtfb.sportshub.backend.roster;

import java.util.List;

/**
 * 409 response body when a roster add or submit is refused by the league category's eligibility
 * profile. {@code players} lists every failing player (one on add, possibly several on submit).
 */
public record PlayerNotEligibleError(String code, String message, List<IneligiblePlayer> players) {
}
