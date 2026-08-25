package de.dtfb.sportshub.backend.federation;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Federation extends BaseEntity {
    private String name;

    /**
     * The federation's default league rule set — the last fallback when neither a group's tier nor
     * its league sets one (resolution order in docs/09-league-model.md §3). Nullable: when unset the
     * resolver falls back to the historical hardcoded defaults.
     */
    @ManyToOne
    @JoinColumn(name = "default_rule_set_id")
    private LeagueRuleSet defaultRuleSet;

    /**
     * The federation directly above this one in the tree, or {@code null} at the top (the single
     * root federation, e.g. DTFB). A general self-referencing tree, not hardcoded to two levels --
     * see docs for the root federation's narrowly-scoped cross-federation authority
     * ({@link de.dtfb.sportshub.backend.access.auth.AuthorizationService}).
     */
    @ManyToOne
    @JoinColumn(name = "parent_federation_id")
    private Federation parentFederation;

    /** Derived, not persisted: the top of the tree has no parent. */
    public boolean isRoot() {
        return parentFederation == null;
    }
}
