package de.dtfb.sportshub.backend.teamparticipation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamParticipationDto {
    private String id;
    private String teamId;
    private String competitionId;
    /** Read-only: derived from the competition's season. */
    private String seasonId;
    private String poolId;
    private String copiedFromParticipationId;
}
