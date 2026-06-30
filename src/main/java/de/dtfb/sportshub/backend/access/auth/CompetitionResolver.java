package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.discipline.Discipline;
import de.dtfb.sportshub.backend.discipline.DisciplineRepository;
import de.dtfb.sportshub.backend.competition.Competition;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchevent.MatchEventRepository;
import de.dtfb.sportshub.backend.matchset.MatchSetRepository;
import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.pool.PoolRepository;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.round.RoundRepository;
import de.dtfb.sportshub.backend.stage.Stage;
import de.dtfb.sportshub.backend.stage.StageRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves any competition entity up to its owning {@link Competition}, walking the
 * {@code Discipline → Competition} spine (Stage → Discipline, Pool → Stage, Round → Pool, …). Every link
 * is a {@code @ManyToOne} (eagerly fetched), so the whole ancestor chain is populated by the single
 * {@code findById} load — the same assumption the COMPETITION scope check already relies on.
 *
 * <p>Returns {@code null} when the entity or any ancestor is missing; callers treat an unresolved
 * event as "deny" (a global admin is short-circuited earlier, before resolution).
 */
@Component
public class CompetitionResolver {

    private final DisciplineRepository disciplineRepository;
    private final StageRepository stageRepository;
    private final PoolRepository poolRepository;
    private final RoundRepository roundRepository;
    private final MatchDayRepository matchDayRepository;
    private final MatchRepository matchRepository;
    private final MatchSetRepository matchSetRepository;
    private final MatchEventRepository matchEventRepository;

    public CompetitionResolver(DisciplineRepository disciplineRepository,
                                    StageRepository stageRepository,
                                    PoolRepository poolRepository,
                                    RoundRepository roundRepository,
                                    MatchDayRepository matchDayRepository,
                                    MatchRepository matchRepository,
                                    MatchSetRepository matchSetRepository,
                                    MatchEventRepository matchEventRepository) {
        this.disciplineRepository = disciplineRepository;
        this.stageRepository = stageRepository;
        this.poolRepository = poolRepository;
        this.roundRepository = roundRepository;
        this.matchDayRepository = matchDayRepository;
        this.matchRepository = matchRepository;
        this.matchSetRepository = matchSetRepository;
        this.matchEventRepository = matchEventRepository;
    }

    public Competition ofDiscipline(String id) {
        return id == null ? null : competitionOf(disciplineRepository.findById(id).orElse(null));
    }

    public Competition ofStage(String id) {
        return id == null ? null : competitionOf(stageRepository.findById(id).orElse(null));
    }

    public Competition ofPool(String id) {
        return id == null ? null : competitionOf(poolRepository.findById(id).orElse(null));
    }

    public Competition ofRound(String id) {
        return id == null ? null : competitionOf(roundRepository.findById(id).orElse(null));
    }

    public Competition ofMatchDay(String id) {
        return id == null ? null : competitionOf(matchDayRepository.findById(id).orElse(null));
    }

    public Competition ofMatch(String id) {
        return id == null ? null : competitionOf(matchRepository.findById(id).orElse(null));
    }

    public Competition ofMatchSet(String id) {
        return id == null ? null
            : matchSetRepository.findById(id).map(ms -> competitionOf(ms.getMatch())).orElse(null);
    }

    public Competition ofMatchEvent(String id) {
        return id == null ? null
            : matchEventRepository.findById(id).map(me -> competitionOf(me.getMatch())).orElse(null);
    }

    // --- null-safe walk, one overload per spine level ---

    private Competition competitionOf(Discipline discipline) {
        return discipline == null ? null : discipline.getCompetition();
    }

    private Competition competitionOf(Stage stage) {
        return stage == null ? null : competitionOf(stage.getDiscipline());
    }

    private Competition competitionOf(Pool pool) {
        return pool == null ? null : competitionOf(pool.getStage());
    }

    private Competition competitionOf(Round round) {
        return round == null ? null : competitionOf(round.getPool());
    }

    private Competition competitionOf(MatchDay matchDay) {
        return matchDay == null ? null : competitionOf(matchDay.getRound());
    }

    private Competition competitionOf(Match match) {
        return match == null ? null : competitionOf(match.getMatchDay());
    }
}
