package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.competition.Competition;
import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * Placement (L1): a team's entry into one competition. Region-admin owned; created en masse by
 * copy-forward, then edited — promote/relegate = move {@link #pool}, add/drop = create/delete. A
 * team may hold several participations in one season (e.g. league + cup). The season is the
 * competition's season, so it is not stored separately here.
 */
@Entity
@Getter
@Setter
public class TeamParticipation extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne
    @JoinColumn(name = "competition_id")
    private Competition competition;

    /** The division the team is placed into; null while registered but not yet placed. */
    @ManyToOne
    @JoinColumn(name = "pool_id")
    private Pool pool;

    /** The participation this was cloned from by copy-forward (promotion/relegation history). */
    private String copiedFromParticipationId;

    /** Lifecycle of this team's roster (L2): DRAFT until the team submits, then admin-confirmed. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RosterStatus rosterStatus = RosterStatus.DRAFT;
}
