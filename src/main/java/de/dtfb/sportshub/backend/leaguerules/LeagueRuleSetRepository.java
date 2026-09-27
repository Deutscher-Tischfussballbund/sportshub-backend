package de.dtfb.sportshub.backend.leaguerules;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LeagueRuleSetRepository extends JpaRepository<LeagueRuleSet, String> {
    /** Rule sets owned by a region (excludes global templates). */
    List<LeagueRuleSet> findByFederationId(String federationId);

    /** The blueprint library -- snapshots are private to their league/tier (docs/21). */
    List<LeagueRuleSet> findBySnapshotFalse();

    /**
     * Templates (blueprints) of one owner -- a federation, or DTFB-wide when {@code federationId} is
     * null -- whose name matches case-insensitively. Backs the unique-template-name rule (docs/21).
     */
    @Query("select r from LeagueRuleSet r left join r.federation f where r.snapshot = false"
        + " and lower(r.name) = lower(:name)"
        + " and ((:federationId is null and f is null) or f.id = :federationId)")
    List<LeagueRuleSet> findTemplatesByOwnerAndName(String federationId, String name);

    /** Snapshots copied from the given blueprint. */
    List<LeagueRuleSet> findBySourceBlueprintId(String blueprintId);
}
