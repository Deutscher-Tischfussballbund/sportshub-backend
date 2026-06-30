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
