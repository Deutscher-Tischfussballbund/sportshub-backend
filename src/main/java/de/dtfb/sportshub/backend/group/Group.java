package de.dtfb.sportshub.backend.group;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.tier.Tier;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;
import lombok.Setter;

/**
 * A group within a tier: a round-robin table of placed teams (e.g. "Gruppe A"), or a knock-out stage
 * ({@link GroupKind#KNOCKOUT}, docs/34) whose fixtures are fed by group places and earlier results.
 * Renamed from the old {@code Pool}. How a group plays comes from the effective {@code LeagueRuleSet}:
 * its own override ({@link #ruleSet}, e.g. the K.O. blueprint on a knock-out group), else the tier's,
 * else the league's.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'ROUND_ROBIN'") // rows inserted without it (dev seed) are round robins
    private GroupKind kind = GroupKind.ROUND_ROBIN;

    /** Optional override: a snapshot private to this group (docs/21, docs/34); null = the tier's/league's rules apply. */
    @ManyToOne
    @JoinColumn(name = "rule_set_id")
    private LeagueRuleSet ruleSet;
}
