package de.dtfb.sportshub.backend.tier;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TierDto {
    private String id;
    private String name;
    private String leagueId;
    /** Ordinal ladder position (1 = top tier); defines promote/relegate order. */
    private Integer level;
    /** Optional own rule set; null = inherit league/federation default. */
    private String ruleSetId;
}
