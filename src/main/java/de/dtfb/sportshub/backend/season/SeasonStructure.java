package de.dtfb.sportshub.backend.season;

import de.dtfb.sportshub.backend.matchday.ResultState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Season-scoped queries over the league subtree (League -> Tier -> Group -> Round -> MatchDay ->
 * Match -> Set/Event, plus Standing). Centralizes the spine-walking JPQL in one place instead of
 * spreading it across ten repositories. The group entity is {@code LeagueGroup} in JPQL (the Java
 * class is {@code Group}, but {@code GROUP} is reserved).
 *
 * <p>Bulk deletes use {@code IN (subquery)} form: the deep association navigation lives in the
 * subquery's SELECT (where Hibernate's implicit joins are supported), while each DELETE's own WHERE
 * only tests a single-valued path - keeping the HQL bulk-delete valid.
 */
@Component
public class SeasonStructure {

    @PersistenceContext
    private EntityManager em;

    public SeasonContents contentsOf(String seasonId) {
        return new SeasonContents(
            count("select count(l) from League l where l.season.id = :s", seasonId),
            count("select count(md) from MatchDay md"
                + " where md.round.group.tier.league.season.id = :s", seasonId),
            countMatchDaysWithResults(seasonId),
            count("select count(st) from Standing st"
                + " where st.group.tier.league.season.id = :s", seasonId));
    }

    /** Delete the whole (assumed result-free) structure under the season, leaf -> root. */
    public void deleteStructure(String seasonId) {
        bulkDelete("delete from MatchEvent e where e.match in"
            + " (select m from Match m where m.matchDay.round.group.tier.league.season.id = :s)",
            seasonId);
        bulkDelete("delete from MatchSet x where x.match in"
            + " (select m from Match m where m.matchDay.round.group.tier.league.season.id = :s)",
            seasonId);
        bulkDelete("delete from Match m where m.matchDay in"
            + " (select md from MatchDay md where md.round.group.tier.league.season.id = :s)",
            seasonId);
        bulkDelete("delete from MatchDay md where md.round in"
            + " (select r from Round r where r.group.tier.league.season.id = :s)", seasonId);
        bulkDelete("delete from Standing st where st.group in"
            + " (select g from LeagueGroup g where g.tier.league.season.id = :s)", seasonId);
        bulkDelete("delete from Round r where r.group in"
            + " (select g from LeagueGroup g where g.tier.league.season.id = :s)", seasonId);
        bulkDelete("delete from LeagueGroup g where g.tier in"
            + " (select t from Tier t where t.league.season.id = :s)", seasonId);
        bulkDelete("delete from Tier t where t.league in"
            + " (select l from League l where l.season.id = :s)", seasonId);
        bulkDelete("delete from League l where l.season.id = :s", seasonId);
    }

    private long countMatchDaysWithResults(String seasonId) {
        return em.createQuery("select count(md) from MatchDay md"
                + " where md.resultState <> :open"
                + " and md.round.group.tier.league.season.id = :s", Long.class)
            .setParameter("open", ResultState.OPEN)
            .setParameter("s", seasonId)
            .getSingleResult();
    }

    private long count(String jpql, String seasonId) {
        return em.createQuery(jpql, Long.class).setParameter("s", seasonId).getSingleResult();
    }

    private void bulkDelete(String jpql, String seasonId) {
        em.createQuery(jpql).setParameter("s", seasonId).executeUpdate();
    }
}
