package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.group.Group;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One side of a knock-out fixture before its team is known (docs/34): a place of a group
 * ({@link FixtureSourceType#GROUP_PLACE}: {@link #group} + {@link #place}) or the winner/loser of another
 * fixture ({@link #matchDay}). The side's team is filled from it once the source is decided.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class FixtureSource {
    @Enumerated(EnumType.STRING)
    private FixtureSourceType type;

    // Lazy: an eager self-reference (fixture → source fixture → its source …) would join without end.
    @ManyToOne(fetch = FetchType.LAZY)
    private Group group;

    /** 1-based place in the group's table (GROUP_PLACE only). */
    private Integer place;

    @ManyToOne(fetch = FetchType.LAZY)
    private MatchDay matchDay;

    public static FixtureSource groupPlace(Group group, int place) {
        FixtureSource source = new FixtureSource();
        source.setType(FixtureSourceType.GROUP_PLACE);
        source.setGroup(group);
        source.setPlace(place);
        return source;
    }

    public static FixtureSource winnerOf(MatchDay matchDay) {
        return of(FixtureSourceType.WINNER, matchDay);
    }

    public static FixtureSource loserOf(MatchDay matchDay) {
        return of(FixtureSourceType.LOSER, matchDay);
    }

    private static FixtureSource of(FixtureSourceType type, MatchDay matchDay) {
        FixtureSource source = new FixtureSource();
        source.setType(type);
        source.setMatchDay(matchDay);
        return source;
    }
}
