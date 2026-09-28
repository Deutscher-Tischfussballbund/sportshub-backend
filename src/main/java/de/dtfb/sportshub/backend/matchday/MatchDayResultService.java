package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
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
import java.util.Set;

/**
 * Result entry and confirmation of a fixture (docs/17, SPO-15). Any team member of either side
 * enters or edits; a result is final ({@code CONFIRMED}) once each side's captain has agreed to the
 * current version -- a captain agrees by confirming, or by entering/editing it themselves. An edit
 * cancels the other side's agreement. A neutral admin's entry, edit or confirmation is final at
 * once, and only a neutral admin may change a final result. Every finalization publishes
 * {@link MatchDayConfirmedEvent}, from which the standings are recomputed.
 */
@Service
public class MatchDayResultService {

    private final MatchDayRepository repository;
    private final MatchRepository matchRepository;
    private final AuthorizationService authz;
    private final ApplicationEventPublisher eventPublisher;

    public MatchDayResultService(MatchDayRepository repository, MatchRepository matchRepository,
                                 AuthorizationService authz, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.matchRepository = matchRepository;
        this.authz = authz;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public MatchDayResultDto get(String matchDayId) {
        MatchDay matchDay = repository.findVisibleById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        return toDto(matchDay, authz.resultActor(matchDay));
    }

    /** Enters or edits the result (docs/17 "Who may enter or edit"). */
    @Transactional
    public MatchDayResultDto enter(String matchDayId, MatchDayResultRequest request, String dtfbId) {
        MatchDay matchDay = repository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        ResultActor actor = authz.resultActor(matchDay);

        if (actor.neutralAdmin()) {
            applyScores(matchDay, request);
            Instant now = Instant.now();
            matchDay.setSubmittedByDtfbId(dtfbId);
            matchDay.setHomeConfirmedAt(now);
            matchDay.setAwayConfirmedAt(now);
            return finalize(matchDay, actor);
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
        applyScores(matchDay, request);
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
        if (matchDay.getResultState() != ResultState.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no entered result to confirm");
        }

        Instant now = Instant.now();
        if (actor.neutralAdmin()) {
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
        if (agreedAt(matchDay, side) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Your team has already agreed; waiting for the other team's captain");
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

    private MatchDayResultDto finalize(MatchDay matchDay, ResultActor actor) {
        matchDay.setResultState(ResultState.CONFIRMED);
        MatchDay saved = repository.save(matchDay);
        eventPublisher.publishEvent(new MatchDayConfirmedEvent(this, saved));
        return toDto(saved, actor);
    }

    /**
     * Writes the scores of the listed games. Checks only structure: the games belong to this fixture,
     * each at most once, scores present and not negative. Whether the scores are a valid result under
     * the rule set (sets, points per set, "first to N") waits for the Regionalliga format (SPO-58).
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
    }

    private static Instant agreedAt(MatchDay matchDay, ResultActor.Side side) {
        return side == ResultActor.Side.HOME ? matchDay.getHomeConfirmedAt() : matchDay.getAwayConfirmedAt();
    }

    private MatchDayResultDto toDto(MatchDay matchDay, ResultActor actor) {
        MatchDayResultDto dto = new MatchDayResultDto();
        dto.setMatchDayId(matchDay.getId());
        dto.setResultState(matchDay.getResultState());
        Team home = matchDay.getTeamHome();
        Team away = matchDay.getTeamAway();
        dto.setHomeTeamId(home == null ? null : home.getId());
        dto.setHomeTeamName(home == null ? null : home.getName());
        dto.setAwayTeamId(away == null ? null : away.getId());
        dto.setAwayTeamName(away == null ? null : away.getName());
        dto.setHomeAgreedAt(matchDay.getHomeConfirmedAt());
        dto.setAwayAgreedAt(matchDay.getAwayConfirmedAt());
        dto.setGames(matchRepository.findByMatchDay(matchDay).stream()
            .sorted(Comparator.comparing(Match::getPosition, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(MatchDayResultService::toGameDto)
            .toList());

        ResultState state = matchDay.getResultState();
        ResultActor.Side memberSide = actor.memberSide();
        ResultActor.Side captainSide = actor.captainSide();
        dto.setNeutralAdmin(actor.neutralAdmin());
        dto.setSide(memberSide);
        dto.setCanEdit(actor.neutralAdmin() || (memberSide != null && state != ResultState.CONFIRMED));
        dto.setCanConfirm(state == ResultState.SUBMITTED
            && (actor.neutralAdmin() || (captainSide != null && agreedAt(matchDay, captainSide) == null)));
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
