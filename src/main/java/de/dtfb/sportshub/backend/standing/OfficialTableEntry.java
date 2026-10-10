package de.dtfb.sportshub.backend.standing;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * One row of a group's frozen official table (docs/29). When a group has these rows, they ARE its
 * official table: {@link StandingService} serves them instead of computing one from the results. Today
 * they come from the Sports Manager's final tables of imported seasons, which follow the SM's own rules
 * (penalty points, quotient orders, shared places) that a recomputed table can't reproduce.
 */
@Entity
@Table(name = "official_table_entry", uniqueConstraints = @UniqueConstraint(
    name = "UK_official_table_entry_group_team", columnNames = {"group_id", "team_id"}))
@Getter
@Setter
public class OfficialTableEntry extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    /** Null = no place (e.g. withdrawn); may repeat for shared places. */
    private Integer place;

    private int played;
    private int won;
    private int drawn;
    private int lost;
    private int goalsFor;
    private int goalsAgainst;

    /** Total points, adjustment included. */
    private int points;

    /** Bonus or penalty points already contained in {@link #points}; 0 if none. */
    private int pointsAdjustment;

    /** Where the table comes from, e.g. {@code sportsmanager}. */
    @Column(nullable = false, length = 32)
    private String source;
}
