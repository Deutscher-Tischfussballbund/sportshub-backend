package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
import de.dtfb.sportshub.backend.leaguerules.FixtureMode;
import de.dtfb.sportshub.backend.lineup.LineupService;
import de.dtfb.sportshub.backend.lineup.LineupsDto;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.MatchdayDecision;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchNotFoundException;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.match.MatchState;
import de.dtfb.sportshub.backend.match.Winner;
import de.dtfb.sportshub.backend.team.Team;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Result entry and confirmation of a fixture (docs/17, SPO-15). Any team member of either side
 * enters or edits; a result is final ({@code CONFIRMED}) once each side's captain has agreed to the
 * current version -- a captain agrees by confirming, or by entering/editing it themselves. An edit
 * cancels the other side's agreement. A neutral admin's entry, edit or confirmation is final at
 * once, and only a neutral admin may change a final result. Every finalization publishes
 * {@link MatchDayConfirmedEvent}, from which the standings are recomputed.
 *
 * <p>A result only becomes final once it is <b>decided</b> under the rule set's matchday decision:
 * {@code ALL_GAMES} (also when none is set) -- every game has a score; {@code FIRST_TO} -- one side
 * has won {@code matchdayTarget} games (the rest may stay unplayed). Until then captains can agree to
 * the entered games, but nothing finalizes: a confirmation that would is refused (409), and a neutral
 * admin's entry stays pending like a team's.
 *
 * <p>In a {@code RACE} rule set (docs/22) the games are the segments of one running score: every save
 * is checked with {@link RaceScoring} (400 on a violation), and "decided" means the last segment is
 * complete under the end rule. Once decided, the captains have {@code confirmationMinutes} to confirm;
 * after that the teams can neither confirm nor edit (409) and only a neutral admin -- the tournament
 * management -- can. Fixtures against the bye take no result at all.
 */
@Service
public class MatchDayResultService {

    private final MatchDayRepository repository;
    private final MatchRepository matchRepository;
    private final AuthorizationService authz;
    private final ApplicationEventPublisher eventPublisher;
    private final LeagueRuleResolver ruleResolver;
    private final LineupService lineups;

    public MatchDayResultService(MatchDayRepository repository, MatchRepository matchRepository,
                                 AuthorizationService authz, ApplicationEventPublisher eventPublisher,
                                 LeagueRuleResolver ruleResolver, LineupService lineups) {
        this.repository = repository;
        this.matchRepository = matchRepository;
        this.authz = authz;
        this.eventPublisher = eventPublisher;
        this.ruleResolver = ruleResolver;
        this.lineups = lineups;
    }

    @Transactional(readOnly = true)
    public MatchDayResultDto get(String matchDayId) {
        MatchDay matchDay = repository.findVisibleById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        return toDto(matchDay, authz.resultActor(matchDay));
    }

    /**
     * Entered, not yet final results the current user has to act on (docs/22): as a captain of either
     * side (the countdown banner) or as a neutral admin (the league admin's overview), soonest
     * deadline first, overdue ones on top.
     */
    @Transactional(readOnly = true)
    public List<MatchDayResultDto> pending() {
        return repository.findVisibleByResultState(ResultState.SUBMITTED).stream()
            .map(matchDay -> {
                ResultActor actor = authz.resultActor(matchDay);
                return actor.neutralAdmin() || actor.homeCaptain() || actor.awayCaptain() ? toDto(matchDay, actor) : null;
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(MatchDayResultDto::isOverdue).reversed()
                .thenComparing(MatchDayResultDto::getConfirmDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MatchDayResultDto::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /** Enters or edits the result (docs/17 "Who may enter or edit"). */
    @Transactional
    public MatchDayResultDto enter(String matchDayId, MatchDayResultRequest request, String dtfbId) {
        MatchDay matchDay = repository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        ResultActor actor = authz.resultActor(matchDay);
        requireNotBye(matchDay);

        if (actor.neutralAdmin()) {
            applyScores(matchDay, request);
            matchDay.setSubmittedByDtfbId(dtfbId);
            trackDecided(matchDay);
            if (isDecided(matchDay)) {
                Instant now = Instant.now();
                matchDay.setHomeConfirmedAt(now);
                matchDay.setAwayConfirmedAt(now);
                return finalize(matchDay, actor);
            }
            // Not decided yet (e.g. a correction mid-day): pending, no side's agreement carried over.
            matchDay.setHomeConfirmedAt(null);
            matchDay.setAwayConfirmedAt(null);
            matchDay.setResultState(ResultState.SUBMITTED);
            return toDto(repository.save(matchDay), actor);
        }

        ResultActor.Side side = actor.memberSide();
        if (side == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You belong to both teams of this fixture, so you can't enter its result");
        }
        if (matchDay.getResultState() == ResultState.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The result is final; only a league or federation admin can change it");
        }
        requireNotOverdue(matchDay);
        if (lineups.lineupRequired(matchDay) && !lineups.bothSubmitted(matchDay)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Both line-ups must be submitted before a result can be entered (docs/23)");
        }
        applyScores(matchDay, request);
        trackDecided(matchDay);
        Instant ownAgreement = actor.captainSide() == side ? Instant.now() : null;
        matchDay.setHomeConfirmedAt(side == ResultActor.Side.HOME ? ownAgreement : null);
        matchDay.setAwayConfirmedAt(side == ResultActor.Side.AWAY ? ownAgreement : null);
        matchDay.setSubmittedByDtfbId(dtfbId);
        matchDay.setResultState(ResultState.SUBMITTED);
        return toDto(repository.save(matchDay), actor);
    }

    /** A captain agrees to the current version for their side, or a neutral admin finalizes it. */
    @Transactional
    public MatchDayResultDto confirm(String matchDayId) {
        MatchDay matchDay = repository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        ResultActor actor = authz.resultActor(matchDay);
        requireNotBye(matchDay);
        if (matchDay.getResultState() != ResultState.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no entered result to confirm");
        }

        Instant now = Instant.now();
        if (actor.neutralAdmin()) {
            requireDecided(matchDay);
            if (matchDay.getHomeConfirmedAt() == null) {
                matchDay.setHomeConfirmedAt(now);
            }
            if (matchDay.getAwayConfirmedAt() == null) {
                matchDay.setAwayConfirmedAt(now);
            }
            return finalize(matchDay, actor);
        }

        ResultActor.Side side = actor.captainSide();
        if (side == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only a captain of one of the two teams can confirm the result");
        }
        requireNotOverdue(matchDay);
        if (agreedAt(matchDay, side) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Your team has already agreed; waiting for the other team's captain");
        }
        boolean otherSideAgreed = side == ResultActor.Side.HOME
            ? matchDay.getAwayConfirmedAt() != null : matchDay.getHomeConfirmedAt() != null;
        if (otherSideAgreed) {
            requireDecided(matchDay); // this confirmation would finalize
        }
        if (side == ResultActor.Side.HOME) {
            matchDay.setHomeConfirmedAt(now);
        } else {
            matchDay.setAwayConfirmedAt(now);
        }
        if (matchDay.getHomeConfirmedAt() != null && matchDay.getAwayConfirmedAt() != null) {
            return finalize(matchDay, actor);
        }
        return toDto(repository.save(matchDay), actor);
    }

    private void requireDecided(MatchDay matchDay) {
        if (!isDecided(matchDay)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The fixture isn't decided yet under the rule set's matchday decision; enter the missing games first");
        }
    }

    /** Whether the entered games decide the fixture under the rule set's matchday decision (see class doc). */
    private boolean isDecided(MatchDay matchDay) {
        List<Match> games = matchRepository.findByMatchDay(matchDay);
        if (games.isEmpty()) {
            return false;
        }
        LeagueRuleSet rules = rulesOf(matchDay);
        if (isRace(rules)) {
            return RaceScoring.decided(segments(games), RaceScoring.Rules.of(rules, games.size()));
        }
        MatchdayDecision decision = rules == null ? null : rules.getMatchdayDecision();
        Integer target = rules == null ? null : rules.getMatchdayTarget();
        if (decision == MatchdayDecision.FIRST_TO && target != null && target > 0) {
            int homeWins = 0;
            int awayWins = 0;
            for (Match game : games) {
                if (game.getHomeScore() == null || game.getAwayScore() == null) continue;
                if (game.getHomeScore() > game.getAwayScore()) homeWins++;
                else if (game.getAwayScore() > game.getHomeScore()) awayWins++;
            }
            if (Math.max(homeWins, awayWins) >= target) {
                return true;
            }
        }
        // ALL_GAMES, no decision set, or FIRST_TO where nobody reached the target: every game counts.
        return games.stream().allMatch(game -> game.getHomeScore() != null && game.getAwayScore() != null);
    }

    private LeagueRuleSet rulesOf(MatchDay matchDay) {
        return matchDay.getRound() == null ? null : ruleResolver.effectiveFor(matchDay.getRound().getGroup());
    }

    private static boolean isRace(LeagueRuleSet rules) {
        return rules != null && rules.getFixtureMode() == FixtureMode.RACE;
    }

    /** The fixture's games as race segments, in game-plan order. */
    private static List<RaceScoring.Segment> segments(List<Match> games) {
        return games.stream()
            .sorted(Comparator.comparing(Match::getPosition, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(game -> new RaceScoring.Segment(game.getHomeScore(), game.getAwayScore()))
            .toList();
    }

    private static void requireNotBye(MatchDay matchDay) {
        if (matchDay.isBye()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A fixture against the bye has no result to enter");
        }
    }

    /** Remembers when the result first became decided (start of the deadline); cleared if an edit undoes it. */
    private void trackDecided(MatchDay matchDay) {
        if (!isDecided(matchDay)) {
            matchDay.setDecidedAt(null);
        } else if (matchDay.getDecidedAt() == null) {
            matchDay.setDecidedAt(Instant.now());
        }
    }

    /** The end of the time to confirm, or null without a deadline (not decided, or none configured). */
    private Instant confirmDeadline(MatchDay matchDay) {
        LeagueRuleSet rules = rulesOf(matchDay);
        Integer minutes = rules == null ? null : rules.getConfirmationMinutes();
        if (minutes == null || minutes <= 0 || matchDay.getDecidedAt() == null) {
            return null;
        }
        return matchDay.getDecidedAt().plus(minutes, java.time.temporal.ChronoUnit.MINUTES);
    }

    private boolean isOverdue(MatchDay matchDay) {
        Instant deadline = confirmDeadline(matchDay);
        return matchDay.getResultState() == ResultState.SUBMITTED && deadline != null && Instant.now().isAfter(deadline);
    }

    private void requireNotOverdue(MatchDay matchDay) {
        if (isOverdue(matchDay)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The time to confirm has run out; only the tournament management can confirm or change the result now");
        }
    }

    private MatchDayResultDto finalize(MatchDay matchDay, ResultActor actor) {
        matchDay.setResultState(ResultState.CONFIRMED);
        MatchDay saved = repository.save(matchDay);
        eventPublisher.publishEvent(new MatchDayConfirmedEvent(this, saved));
        return toDto(saved, actor);
    }

    /**
     * Writes the scores of the listed games. Checks structure (the games belong to this fixture, each at
     * most once, scores present and not negative) and, in a RACE rule set, the whole race so far
     * ({@link RaceScoring#violation}). Sets per game (GAMES mode) are a later topic (docs/22).
     */
    private void applyScores(MatchDay matchDay, MatchDayResultRequest request) {
        List<MatchDayResultRequest.MatchResultEntry> entries =
            request == null || request.getMatches() == null ? List.of() : request.getMatches();
        if (entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A result needs at least one game score");
        }
        Set<String> seen = new HashSet<>();
        for (MatchDayResultRequest.MatchResultEntry entry : entries) {
            if (!seen.add(entry.getMatchId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A game is listed twice");
            }
            if (entry.getHomeScore() == null || entry.getAwayScore() == null
                    || entry.getHomeScore() < 0 || entry.getAwayScore() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Every game needs a home and an away score of at least 0");
            }
            Match match = matchRepository.findById(entry.getMatchId())
                .orElseThrow(() -> new MatchNotFoundException(entry.getMatchId()));
            if (match.getMatchDay() == null || !match.getMatchDay().getId().equals(matchDay.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match does not belong to this match day");
            }
            match.setHomeScore(entry.getHomeScore());
            match.setAwayScore(entry.getAwayScore());
            match.setWinner(entry.getHomeScore() > entry.getAwayScore() ? Winner.HOME
                : entry.getHomeScore() < entry.getAwayScore() ? Winner.AWAY : Winner.DRAW);
            match.setState(MatchState.PLAYED);
            matchRepository.save(match);
        }
        LeagueRuleSet rules = rulesOf(matchDay);
        if (isRace(rules)) {
            List<Match> games = matchRepository.findByMatchDay(matchDay);
            String violation = RaceScoring.violation(segments(games), RaceScoring.Rules.of(rules, games.size()));
            if (violation != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, violation);
            }
        }
    }

    private static Instant agreedAt(MatchDay matchDay, ResultActor.Side side) {
        return side == ResultActor.Side.HOME ? matchDay.getHomeConfirmedAt() : matchDay.getAwayConfirmedAt();
    }

    private MatchDayResultDto toDto(MatchDay matchDay, ResultActor actor) {
        MatchDayResultDto dto = new MatchDayResultDto();
        dto.setMatchDayId(matchDay.getId());
        dto.setResultState(matchDay.getResultState());
        dto.setStartDate(matchDay.getStartDate());
        if (matchDay.getRound() != null && matchDay.getRound().getGroup() != null) {
            var group = matchDay.getRound().getGroup();
            dto.setGroupId(group.getId());
            dto.setGroupName(group.getName());
            if (group.getTier() != null && group.getTier().getLeague() != null) {
                dto.setLeagueId(group.getTier().getLeague().getId());
                dto.setLeagueName(group.getTier().getLeague().getName());
                if (group.getTier().getLeague().getSeason() != null
                        && group.getTier().getLeague().getSeason().getFederation() != null) {
                    dto.setFederationId(group.getTier().getLeague().getSeason().getFederation().getId());
                }
            }
        }
        Team home = matchDay.getTeamHome();
        Team away = matchDay.getTeamAway();
        dto.setHomeTeamId(home == null ? null : home.getId());
        dto.setHomeTeamName(home == null ? null : home.getName());
        dto.setAwayTeamId(away == null ? null : away.getId());
        dto.setAwayTeamName(away == null ? null : away.getName());
        dto.setHomeTeamIdentityId(home == null ? null : home.getTeamIdentityId());
        dto.setAwayTeamIdentityId(away == null ? null : away.getTeamIdentityId());
        dto.setHomeAgreedAt(matchDay.getHomeConfirmedAt());
        dto.setAwayAgreedAt(matchDay.getAwayConfirmedAt());
        Map<String, List<List<LineupsDto.PlayerRefDto>>> players = lineups.playersForResult(matchDay, actor);
        dto.setGames(matchRepository.findByMatchDay(matchDay).stream()
            .sorted(Comparator.comparing(Match::getPosition, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(game -> {
                MatchDayResultDto.GameResultDto gameDto = toGameDto(game);
                List<List<LineupsDto.PlayerRefDto>> sides = players.get(game.getId());
                if (sides != null) {
                    gameDto.setHomePlayers(sides.get(0));
                    gameDto.setAwayPlayers(sides.get(1));
                }
                return gameDto;
            })
            .toList());
        dto.setLineupRequired(lineups.lineupRequired(matchDay));
        dto.setLineupsComplete(lineups.bothSubmitted(matchDay));

        List<MatchDayResultDto.GameResultDto> games = dto.getGames();
        dto.setGamesTotal(games.size());
        dto.setGamesEntered((int) games.stream().filter(g -> g.getHomeScore() != null && g.getAwayScore() != null).count());
        boolean decided = isDecided(matchDay);
        dto.setDecided(decided);
        LeagueRuleSet rules = rulesOf(matchDay);
        dto.setBye(matchDay.isBye());
        dto.setFixtureMode(rules == null || rules.getFixtureMode() == null ? FixtureMode.GAMES : rules.getFixtureMode());
        if (isRace(rules)) {
            RaceScoring.Rules race = RaceScoring.Rules.of(rules, games.size());
            dto.setRaceTarget(race.target());
            dto.setRaceStep(race.step());
            dto.setRaceEndRule(race.endRule());
        }
        dto.setDecidedAt(matchDay.getDecidedAt());
        dto.setConfirmDeadline(confirmDeadline(matchDay));
        boolean overdue = isOverdue(matchDay);
        dto.setOverdue(overdue);

        ResultState state = matchDay.getResultState();
        ResultActor.Side memberSide = actor.memberSide();
        ResultActor.Side captainSide = actor.captainSide();
        dto.setNeutralAdmin(actor.neutralAdmin());
        dto.setSide(memberSide);
        boolean lineupsOk = !dto.isLineupRequired() || dto.isLineupsComplete();
        dto.setCanEdit(!matchDay.isBye()
            && (actor.neutralAdmin() || (memberSide != null && state != ResultState.CONFIRMED && !overdue && lineupsOk)));
        boolean otherSideAgreed = captainSide != null && agreedAt(matchDay,
            captainSide == ResultActor.Side.HOME ? ResultActor.Side.AWAY : ResultActor.Side.HOME) != null;
        dto.setCanConfirm(state == ResultState.SUBMITTED && (actor.neutralAdmin()
            ? decided
            : !overdue && captainSide != null && agreedAt(matchDay, captainSide) == null && (decided || !otherSideAgreed)));
        return dto;
    }

    private static MatchDayResultDto.GameResultDto toGameDto(Match match) {
        MatchDayResultDto.GameResultDto game = new MatchDayResultDto.GameResultDto();
        game.setMatchId(match.getId());
        game.setPosition(match.getPosition());
        game.setType(match.getType());
        game.setHomeScore(match.getHomeScore());
        game.setAwayScore(match.getAwayScore());
        return game;
    }
}
