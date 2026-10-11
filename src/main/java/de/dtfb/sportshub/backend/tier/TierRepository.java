package de.dtfb.sportshub.backend.tier;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TierRepository extends JpaRepository<Tier, String> {

    /** Tiers of one league (copy-forward source walk / structure read). */
    List<Tier> findByLeagueId(String leagueId);

    /** Whether the league still has any tier (league delete guard). */
    boolean existsByLeagueId(String leagueId);

    /** Whether any tier still references this rule set (rule-set delete guard). */
    boolean existsByRuleSetId(String ruleSetId);

    /** The tier owning this snapshot as its override, if any (docs/21). */
    Optional<Tier> findFirstByRuleSetId(String ruleSetId);

    @Query("select e from Tier e where e.league.season.archivedAt is null")
    List<Tier> findAllVisible();

    /** Tiers of seasons that aren't archived, narrowed by the optional filters (SPO-120). */
    @Query("""
        select e from Tier e where e.league.season.archivedAt is null
          and (:leagueId is null or e.league.id = :leagueId)
          and (:seasonId is null or e.league.season.id = :seasonId)""")
    List<Tier> findVisible(@Param("leagueId") String leagueId, @Param("seasonId") String seasonId);

    @Query("select e from Tier e where e.id = :id and e.league.season.archivedAt is null")
    Optional<Tier> findVisibleById(String id);
}
