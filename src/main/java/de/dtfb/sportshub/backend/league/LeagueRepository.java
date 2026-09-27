package de.dtfb.sportshub.backend.league;

import de.dtfb.sportshub.backend.season.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface LeagueRepository extends JpaRepository<League, String> {
    Optional<League> findBySeasonAndName(Season season, String name);

    /** Leagues of active (non-archived) seasons -- hides an archived season's subtree. */
    List<League> findBySeason_ArchivedAtIsNull();

    /** Leagues of one season (used by copy-forward to walk the source subtree). */
    List<League> findBySeasonId(String seasonId);

    /** Whether any league still references this rule set (rule-set delete guard). */
    boolean existsByRuleSetId(String ruleSetId);

    /** Every season-copy of a league (SPO-28); LEAGUE_ADMIN grants are scoped to this identity. */
    List<League> findByLeagueIdentityId(String leagueIdentityId);

    /** Every season-copy of any of these leagues. */
    List<League> findByLeagueIdentityIdIn(Collection<String> leagueIdentityIds);

    /** The newest season's copy of a league identity (by season start date) -- for display. */
    default Optional<League> findLatestByLeagueIdentityId(String leagueIdentityId) {
        return findByLeagueIdentityId(leagueIdentityId).stream()
            .max(Comparator.comparing(
                (League l) -> l.getSeason() == null ? null : l.getSeason().getStartDate(),
                Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /**
     * A LEAGUE scope id resolved to a league: a league row id (as sent by older clients) or a league
     * identity (what grants store), the latter resolved to its newest season's copy.
     */
    default Optional<League> findByScopeId(String scopeId) {
        if (scopeId == null) {
            return Optional.empty();
        }
        return findById(scopeId).or(() -> findLatestByLeagueIdentityId(scopeId));
    }

    /** The league owning this snapshot, if any (docs/21 -- a snapshot has exactly one owner). */
    Optional<League> findFirstByRuleSetId(String ruleSetId);
}
