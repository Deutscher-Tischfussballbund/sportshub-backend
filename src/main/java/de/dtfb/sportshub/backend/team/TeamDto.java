package de.dtfb.sportshub.backend.team;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamDto {
    private String id;
    private String seasonId;
    private String name;
    private String clubId;

    /** Read-only: stable across this team's season-copies; use to correlate the same team across seasons. */
    private String teamIdentityId;

    /**
     * Read-only: the club's name as it stood at this team's own season -- reconstructed from
     * {@link de.dtfb.sportshub.backend.history.EntityHistoryService}, not necessarily the club's
     * current name.
     */
    private String clubName;
}
