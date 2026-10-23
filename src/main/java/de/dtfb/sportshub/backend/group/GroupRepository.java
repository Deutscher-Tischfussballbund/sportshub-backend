package de.dtfb.sportshub.backend.group;

import org.springframework.data.repository.query.Param;
import de.dtfb.sportshub.backend.tier.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, String> {
    Optional<Group> findByTierAndName(Tier tier, String name);

    /** Groups of one tier (copy-forward source walk). */
    List<Group> findByTierId(String tierId);

    /** Whether the tier still has any group (tier delete guard). */
    boolean existsByTierId(String tierId);

    boolean existsByRuleSetId(String ruleSetId);

    Optional<Group> findFirstByRuleSetId(String ruleSetId);

    /** All groups under a league, across its tiers (league structure read). */
    List<Group> findByTier_League_Id(String leagueId);

    @Query("select e from LeagueGroup e where e.tier.league.season.archivedAt is null")
    List<Group> findAllVisible();

    /** Groups of seasons that aren't archived, narrowed by the optional filters (SPO-120). */
    @Query("""
        select e from LeagueGroup e where e.tier.league.season.archivedAt is null
          and (:tierId is null or e.tier.id = :tierId)
          and (:leagueId is null or e.tier.league.id = :leagueId)
          and (:seasonId is null or e.tier.league.season.id = :seasonId)""")
    List<Group> findVisible(@Param("tierId") String tierId, @Param("leagueId") String leagueId,
                            @Param("seasonId") String seasonId);

    @Query("select e from LeagueGroup e where e.id = :id and e.tier.league.season.archivedAt is null")
    Optional<Group> findVisibleById(String id);
}
