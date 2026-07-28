package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.group.Group;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class Round extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;

    private String name;

    // Column name overridden — "index" is a reserved MySQL word (unquoted use breaks DDL on real
    // MySQL, though it's silently fine on dev's H2). Java field/JSON API surface stay "index".
    @Column(name = "round_index")
    private Integer index;

    // Only set under LeagueRuleSet.SchedulingMode.WINDOW — the period within which this round's
    // fixtures should be played; see docs/12-matchday-scheduling.md.
    private Instant windowStart;
    private Instant windowEnd;
}
