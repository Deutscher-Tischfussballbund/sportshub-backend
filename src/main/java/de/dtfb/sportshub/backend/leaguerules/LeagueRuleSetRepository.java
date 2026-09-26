package de.dtfb.sportshub.backend.leaguerules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeagueRuleSetRepository extends JpaRepository<LeagueRuleSet, String> {
    /** Rule sets owned by a region (excludes global templates). */
    List<LeagueRuleSet> findByFederationId(String federationId);

    /** The blueprint library -- snapshots are private to their league/tier (docs/21). */
    List<LeagueRuleSet> findBySnapshotFalse();

    /** Snapshots copied from the given blueprint. */
    List<LeagueRuleSet> findBySourceBlueprintId(String blueprintId);
}
