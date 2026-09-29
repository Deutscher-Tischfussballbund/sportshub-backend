package de.dtfb.sportshub.backend.lineup;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One team's line-up for one fixture (docs/23): who plays which game of the game plan, entered by the
 * team's captain before kick-off. A draft until {@link #submittedAt} is set; hidden from the opponent
 * until both teams have submitted, then locked -- from then on only substitutions change who plays.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"match_day_id", "team_id"}))
@Getter
@Setter
public class Lineup extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "match_day_id")
    private MatchDay matchDay;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    /** Null while a draft. */
    private Instant submittedAt;

    private String submittedByDtfbId;
}
