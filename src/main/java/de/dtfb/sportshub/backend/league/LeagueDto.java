package de.dtfb.sportshub.backend.league;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeagueDto {
    private String id;
    private String name;
    private String seasonId;
    /** Read-only: stable across this league's season-copies (SPO-28); what LEAGUE_ADMIN grants point to. */
    private String leagueIdentityId;
    /** The category this league runs under (Herren/Damen/...). Required on create. */
    private String categoryId;
    /**
     * Read-only: the league's own rule set -- a snapshot private to this league (docs/21). Edit it
     * via {@code PUT /v1/league-rule-sets/{ruleSetId}} while the season runs.
     */
    private String ruleSetId;
    /** Read-only: the snapshot's name (initially the blueprint's). */
    private String ruleSetName;
    /**
     * The blueprint the league's rules come from. On create: which blueprint to copy (null = the
     * federation's default). On update: a different blueprint than the current one resets the
     * league's rules from it (refused once the season has ended); null or unchanged keeps them.
     */
    private String blueprintId;
}
