package de.dtfb.sportshub.backend.matchday;

import lombok.Getter;
import lombok.Setter;

/** One side's source of a knock-out fixture (docs/34), with names for display ("1. Gruppe A", "Sieger HF 1"). */
@Getter
@Setter
public class FixtureSourceDto {
    private FixtureSourceType type;
    /** GROUP_PLACE: the source group and the 1-based place in its table. */
    private String groupId;
    private String groupName;
    private Integer place;
    /** WINNER / LOSER: the source fixture and its label. */
    private String matchDayId;
    private String matchDayName;
}
