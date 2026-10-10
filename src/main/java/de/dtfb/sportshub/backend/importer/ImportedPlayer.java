package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.player.PlayerGender;

/**
 * A player as the source has it. {@code number} is the player number ({@code SS-NNNN}), null when the
 * source has none. {@code genderRaw} is the source's own value, kept so the planner can tell "missing"
 * from "not understood" ({@code gender} null in both cases).
 */
public record ImportedPlayer(String externalId, String number, String firstName, String lastName,
                             Integer birthYear, PlayerGender gender, String genderRaw,
                             String nationalLicense, String internationalId) {
}
