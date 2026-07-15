package de.dtfb.sportshub.backend.group;

import de.dtfb.sportshub.backend.tier.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, String> {
    Optional<Group> findByTierAndName(Tier tier, String name);

    /** Groups of one tier (copy-forward source walk). */
    List<Group> findByTierId(String tierId);

    /** All groups under a league, across its tiers (league structure read). */
    List<Group> findByTier_League_Id(String leagueId);

    @Query("select e from LeagueGroup e where e.tier.league.season.archivedAt is null")
    List<Group> findAllVisible();

    @Query("select e from LeagueGroup e where e.id = :id and e.tier.league.season.archivedAt is null")
    Optional<Group> findVisibleById(String id);
}
