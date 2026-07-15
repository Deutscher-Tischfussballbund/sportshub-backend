package de.dtfb.sportshub.backend.teamparticipation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamParticipationDto {
    private String id;
    private String teamId;
    private String leagueId;
    /** Read-only: derived from the league's season. */
    private String seasonId;
    private String groupId;
    private String copiedFromParticipationId;
    /** Read-only: the roster lifecycle state; changed via the roster submit/confirm/reopen endpoints. */
    private RosterStatus rosterStatus;
}
