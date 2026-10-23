package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.location.Location;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
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

    /**
     * The fixture against the bye (docs/22): {@code teamHome} sits the round out, {@code teamAway} is
     * null, and the rule set's bye score counts as its win. Created final by the generator; no games.
     */
    @Column(nullable = false)
    @ColumnDefault("false") // rows inserted without it (dev seed, V16 on existing data) are regular fixtures
    private boolean bye;

    /** When the result first became decided -- start of the confirmation deadline (docs/22). */
    private Instant decidedAt;

    /**
     * When the result first became final (docs/17); never cleared. From then on only a neutral admin
     * may enter or confirm, even if a correction makes the result undecided again.
     */
    private Instant firstFinalAt;

    // Scheduling lifecycle for startDate/location above — separate from the result lifecycle.
    // See docs/12-matchday-scheduling.md.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SchedulingState schedulingState = SchedulingState.DEFAULT;

    private String scheduleProposedByDtfbId;

    private Instant scheduleConfirmedAt;

    // Knock-out stage (docs/34): where each side's team comes from, until it's filled; null for a fixture
    // of placed teams.
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "type", column = @Column(name = "home_source_type")),
        @AttributeOverride(name = "place", column = @Column(name = "home_source_place")),
    })
    @AssociationOverrides({
        @AssociationOverride(name = "group", joinColumns = @JoinColumn(name = "home_source_group_id")),
        @AssociationOverride(name = "matchDay", joinColumns = @JoinColumn(name = "home_source_match_day_id")),
    })
    private FixtureSource homeSource;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "type", column = @Column(name = "away_source_type")),
        @AttributeOverride(name = "place", column = @Column(name = "away_source_place")),
    })
    @AssociationOverrides({
        @AssociationOverride(name = "group", joinColumns = @JoinColumn(name = "away_source_group_id")),
        @AssociationOverride(name = "matchDay", joinColumns = @JoinColumn(name = "away_source_match_day_id")),
    })
    private FixtureSource awaySource;

    /** The side's team was set by an admin (lot, penalty, correction) and is never overwritten from its source. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean homeSetByHand;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean awaySetByHand;

    /** Final-ranking place the winner/loser of this knock-out fixture takes (e.g. final 1/2, game for 3rd 3/4). */
    private Integer winnerPlace;
    private Integer loserPlace;

    /** Final now, or final once before an admin's correction reopened it: only neutral admins act on it (docs/17). */
    public boolean hasBeenFinal() {
        return resultState == ResultState.CONFIRMED || firstFinalAt != null;
    }
}
