package de.dtfb.sportshub.backend.standing;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"pool_id", "team_id"}))
public class Standing extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "pool_id")
    private Pool pool;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    private int played;
    private int wins;
    private int draws;
    private int losses;
    private int points;
    private int setsWon;
    private int setsLost;
}
