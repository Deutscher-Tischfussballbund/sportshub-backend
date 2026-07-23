package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Placement (L1): a team's entry into one league. Region-admin owned; created en masse by
 * copy-forward, then edited -- promote/relegate = move {@link #group}, add/drop = create/delete. A
 * team may hold several participations in one season (e.g. two leagues). The season is the league's
 * season, so it is not stored separately here.
 */
@Entity
@Getter
@Setter
public class TeamParticipation extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne
    @JoinColumn(name = "league_id")
    private League league;

    /** The group the team is placed into; null while registered but not yet placed. */
    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;

    /** The participation this was cloned from by copy-forward (promotion/relegation history). */
    private String copiedFromParticipationId;

    /** Lifecycle of this team's roster (L2): DRAFT until the team submits, then admin-confirmed. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RosterStatus rosterStatus = RosterStatus.DRAFT;

    /** Whether the team is still active in this league, or has withdrawn. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipationStatus status = ParticipationStatus.ACTIVE;

    /** Set when {@link #status} transitions to {@link ParticipationStatus#WITHDRAWN}. */
    private Instant withdrawnAt;
}
