package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.location.Location;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class MatchDay extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "round_id")
    private Round round;

    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne
    @JoinColumn(name = "team_home_id")
    private Team teamHome;

    @ManyToOne
    @JoinColumn(name = "team_away_id")
    private Team teamAway;

    private String name;

    @Column(nullable = false)
    private Instant startDate;

    private Instant endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultState resultState = ResultState.OPEN;

    private String submittedByDtfbId;

    private Instant homeConfirmedAt;

    private Instant awayConfirmedAt;
}
