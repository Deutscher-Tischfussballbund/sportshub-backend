package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class Match extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "match_day_id")
    private MatchDay matchDay;

    /**
     * Order of this game within its matchday (1-based); maps to the {@code GamePlanEntry.position}
     * of the rule set's game plan — see docs/09-league-model.md §3.1.
     */
    private Integer position;

    @Column(nullable = false)
    private Instant startTime;

    private Instant endTime;

    private Integer homeScore;

    private Integer awayScore;

    @Enumerated(EnumType.STRING)
    private MatchState state;

    @Enumerated(EnumType.STRING)
    private MatchType type;

    @Enumerated(EnumType.STRING)
    private Winner winner;
}
