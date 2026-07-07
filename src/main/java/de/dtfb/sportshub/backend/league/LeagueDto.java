package de.dtfb.sportshub.backend.league;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeagueDto {
    private String id;
    private String name;
    private String seasonId;
    /** The category this league runs under (Herren/Damen/...). Required on create. */
    private String categoryId;
    /** Optional league-level default rule set; null = inherit a federation default. */
    private String ruleSetId;
}
