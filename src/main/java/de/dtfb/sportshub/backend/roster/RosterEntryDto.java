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
    private Instant addedAt;
    private Instant removedAt;
}
