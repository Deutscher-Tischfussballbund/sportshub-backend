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
    /**
     * The tier's own rules override: a snapshot private to this tier (docs/21), or null = the
     * league's rules apply. On update, sending null here together with a null {@link #blueprintId}
     * removes an existing override; echoing the current id keeps it.
     */
    private String ruleSetId;
    /** Read-only: the override snapshot's name. */
    private String ruleSetName;
    /**
     * The blueprint of the override. On create: set = give the tier an override copied from it. On
     * update: a different blueprint than the current one (or one where there's no override yet)
     * resets/creates the override from it. Refused once the season has ended.
     */
    private String blueprintId;
}
