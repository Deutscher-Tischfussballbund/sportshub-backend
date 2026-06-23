package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.discipline.Discipline;
import de.dtfb.sportshub.backend.discipline.DisciplineRepository;
import de.dtfb.sportshub.backend.event.Event;
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
 * Resolves any competition entity up to its owning {@link Event}, walking the
 * {@code Discipline → Event} spine (Stage → Discipline, Pool → Stage, Round → Pool, …). Every link
 * is a {@code @ManyToOne} (eagerly fetched), so the whole ancestor chain is populated by the single
 * {@code findById} load — the same assumption the EVENT scope check already relies on.
 *
 * <p>Returns {@code null} when the entity or any ancestor is missing; callers treat an unresolved
 * event as "deny" (a global admin is short-circuited earlier, before resolution).
 */
@Component
public class CompetitionEventResolver {

    private final DisciplineRepository disciplineRepository;
    private final StageRepository stageRepository;
    private final PoolRepository poolRepository;
    private final RoundRepository roundRepository;
    private final MatchDayRepository matchDayRepository;
    private final MatchRepository matchRepository;
    private final MatchSetRepository matchSetRepository;
    private final MatchEventRepository matchEventRepository;

    public CompetitionEventResolver(DisciplineRepository disciplineRepository,
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

    public Event ofDiscipline(String id) {
        return id == null ? null : eventOf(disciplineRepository.findById(id).orElse(null));
    }

    public Event ofStage(String id) {
        return id == null ? null : eventOf(stageRepository.findById(id).orElse(null));
    }

    public Event ofPool(String id) {
        return id == null ? null : eventOf(poolRepository.findById(id).orElse(null));
    }

    public Event ofRound(String id) {
        return id == null ? null : eventOf(roundRepository.findById(id).orElse(null));
    }

    public Event ofMatchDay(String id) {
        return id == null ? null : eventOf(matchDayRepository.findById(id).orElse(null));
    }

    public Event ofMatch(String id) {
        return id == null ? null : eventOf(matchRepository.findById(id).orElse(null));
    }

    public Event ofMatchSet(String id) {
        return id == null ? null
            : matchSetRepository.findById(id).map(ms -> eventOf(ms.getMatch())).orElse(null);
    }

    public Event ofMatchEvent(String id) {
        return id == null ? null
            : matchEventRepository.findById(id).map(me -> eventOf(me.getMatch())).orElse(null);
    }

    // --- null-safe walk, one overload per spine level ---

    private Event eventOf(Discipline discipline) {
        return discipline == null ? null : discipline.getEvent();
    }

    private Event eventOf(Stage stage) {
        return stage == null ? null : eventOf(stage.getDiscipline());
    }

    private Event eventOf(Pool pool) {
        return pool == null ? null : eventOf(pool.getStage());
    }

    private Event eventOf(Round round) {
        return round == null ? null : eventOf(round.getPool());
    }

    private Event eventOf(MatchDay matchDay) {
        return matchDay == null ? null : eventOf(matchDay.getRound());
    }

    private Event eventOf(Match match) {
        return match == null ? null : eventOf(match.getMatchDay());
    }
}
