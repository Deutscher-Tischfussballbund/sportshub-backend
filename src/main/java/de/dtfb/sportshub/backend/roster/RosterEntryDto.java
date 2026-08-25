package de.dtfb.sportshub.backend.roster;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class RosterEntryDto {
    private String id;
    private String participationId;
    private String playerId;

    /**
     * Read-only: the player's name as it stood when they were added to this roster ({@link #addedAt}),
     * reconstructed from {@link de.dtfb.sportshub.backend.history.EntityHistoryService} -- not
     * necessarily the player's current name.
     */
    private String firstName;
    private String lastName;

    private Instant addedAt;
    private Instant removedAt;
}
