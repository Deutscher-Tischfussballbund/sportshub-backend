package de.dtfb.sportshub.backend.season;

import de.dtfb.sportshub.backend.matchday.ResultState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Season-scoped queries over the competition subtree (Competition → Discipline → Stage → Pool →
 * Round → MatchDay → Match → Set/Event, plus Standing). Centralizes the spine-walking JPQL in one
 * place instead of spreading it across ten repositories.
 *
 * <p>Bulk deletes use {@code IN (subquery)} form: the deep association navigation lives in the
 * subquery's SELECT (where Hibernate's implicit joins are supported), while each DELETE's own WHERE
 * only tests a single-valued path — keeping the HQL bulk-delete valid.
 */
@Component
public class SeasonStructure {

    @PersistenceContext
    private EntityManager em;

    public SeasonContents contentsOf(String seasonId) {
        return new SeasonContents(
            count("select count(c) from Competition c where c.season.id = :s", seasonId),
            count("select count(md) from MatchDay md"
                + " where md.round.pool.stage.discipline.competition.season.id = :s", seasonId),
            countMatchDaysWithResults(seasonId),
            count("select count(st) from Standing st"
                + " where st.pool.stage.discipline.competition.season.id = :s", seasonId));
    }

    /** Delete the whole (assumed result-free) structure under the season, leaf → root. */
    public void deleteStructure(String seasonId) {
        bulkDelete("delete from MatchEvent e where e.match in"
            + " (select m from Match m where m.matchDay.round.pool.stage.discipline.competition.season.id = :s)",
            seasonId);
        bulkDelete("delete from MatchSet x where x.match in"
            + " (select m from Match m where m.matchDay.round.pool.stage.discipline.competition.season.id = :s)",
            seasonId);
        bulkDelete("delete from Match m where m.matchDay in"
            + " (select md from MatchDay md where md.round.pool.stage.discipline.competition.season.id = :s)",
            seasonId);
        bulkDelete("delete from MatchDay md where md.round in"
            + " (select r from Round r where r.pool.stage.discipline.competition.season.id = :s)", seasonId);
        bulkDelete("delete from Standing st where st.pool in"
            + " (select p from Pool p where p.stage.discipline.competition.season.id = :s)", seasonId);
        bulkDelete("delete from Round r where r.pool in"
            + " (select p from Pool p where p.stage.discipline.competition.season.id = :s)", seasonId);
        bulkDelete("delete from Pool p where p.stage in"
            + " (select sg from Stage sg where sg.discipline.competition.season.id = :s)", seasonId);
        bulkDelete("delete from Stage sg where sg.discipline in"
            + " (select d from Discipline d where d.competition.season.id = :s)", seasonId);
        bulkDelete("delete from Discipline d where d.competition in"
            + " (select c from Competition c where c.season.id = :s)", seasonId);
        bulkDelete("delete from Competition c where c.season.id = :s", seasonId);
    }

    private long countMatchDaysWithResults(String seasonId) {
        return em.createQuery("select count(md) from MatchDay md"
                + " where md.resultState <> :open"
                + " and md.round.pool.stage.discipline.competition.season.id = :s", Long.class)
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
