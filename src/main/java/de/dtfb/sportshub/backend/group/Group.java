package de.dtfb.sportshub.backend.group;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.tier.Tier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A group: one round-robin table of teams within a tier (e.g. "Gruppe A"). Renamed from the old
 * {@code Pool}; the tournament-format discriminator ({@code tournamentMode}) is dropped -- how a
 * group plays comes from the tier/league {@code LeagueRuleSet} now.
 *
 * <p>The JPA entity name is {@code LeagueGroup} and the table is {@code comp_group} because
 * {@code GROUP} is a reserved word in both SQL and JPQL; all JPQL refers to {@code LeagueGroup}.
 */
@Entity(name = "LeagueGroup")
@Table(name = "comp_group")
@Getter
@Setter
public class Group extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "tier_id")
    private Tier tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupState groupState;

    private String name;
}
