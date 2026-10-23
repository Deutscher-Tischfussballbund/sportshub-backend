package de.dtfb.sportshub.backend.knockout;

import de.dtfb.sportshub.backend.matchday.FixtureSourceType;
import lombok.Getter;
import lombok.Setter;

/**
 * Body of {@code PUT /v1/matchdays/{id}/knockout} (docs/34): a knock-out fixture's sources, places and
 * team overrides. A side with a {@code teamId} is set by hand and no longer filled from its source; a side
 * without one is filled automatically again. Allowed until the fixture has a result.
 */
@Getter
@Setter
public class KnockoutFixtureRequest {
    private Source homeSource;
    private Source awaySource;
    private Integer winnerPlace;
    private Integer loserPlace;
    private String homeTeamId;
    private String awayTeamId;

    @Getter
    @Setter
    public static class Source {
        private FixtureSourceType type;
        private String groupId;
        private Integer place;
        private String matchDayId;
    }
}
