package de.dtfb.sportshub.backend.knockout;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupKind;
import de.dtfb.sportshub.backend.group.GroupNotFoundException;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.matchday.FixtureScoreService;
import de.dtfb.sportshub.backend.matchday.FixtureSource;
import de.dtfb.sportshub.backend.matchday.FixtureSourceType;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayConfirmedEvent;
import de.dtfb.sportshub.backend.matchday.MatchDayDto;
import de.dtfb.sportshub.backend.matchday.MatchDayMapper;
import de.dtfb.sportshub.backend.matchday.MatchDayNotFoundException;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.standing.StandingDto;
import de.dtfb.sportshub.backend.standing.StandingService;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamNotFoundException;
import de.dtfb.sportshub.backend.team.TeamRepository;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Knock-out stages (docs/34): fixtures of a {@link GroupKind#KNOCKOUT} group name a source per side -- a
 * group place, or the winner/loser of another fixture -- and get their teams filled once the source is
 * decided. A side set by hand (lot, penalty, correction) is never overwritten. Teams are only (re)filled
 * while the fixture has no result; a result correction that would change a team of a later fixture
 * with a result is refused ({@code 409 KNOCKOUT_DOWNSTREAM_PLAYED}).
 */
@Service
public class KnockoutService {

    private final MatchDayRepository matchDayRepository;
    private final GroupRepository groupRepository;
    private final StandingService standingService;
    private final FixtureScoreService scoreService;
    private final TeamRepository teamRepository;
    private final TeamParticipationRepository participationRepository;
    private final MatchDayMapper matchDayMapper;

    public KnockoutService(MatchDayRepository matchDayRepository, GroupRepository groupRepository,
                           StandingService standingService, FixtureScoreService scoreService,
                           TeamRepository teamRepository, TeamParticipationRepository participationRepository,
                           MatchDayMapper matchDayMapper) {
        this.matchDayRepository = matchDayRepository;
        this.groupRepository = groupRepository;
        this.standingService = standingService;
        this.scoreService = scoreService;
        this.teamRepository = teamRepository;
        this.participationRepository = participationRepository;
        this.matchDayMapper = matchDayMapper;
    }

    /** A fixture became final: fill what it decides -- later fixtures and, if its group is now finished, group places. */
    @EventListener
    @Transactional
    public void onMatchDayConfirmed(MatchDayConfirmedEvent event) {
        refreshAfter(event.getMatchDay());
    }

    /** Re-fills the knock-out stages of the fixture's tier, e.g. after it became final or a correction reopened it. */
    @Transactional
    public void refreshAfter(MatchDay matchDay) {
        Group group = matchDay.getRound() == null ? null : matchDay.getRound().getGroup();
        if (group != null) {
            refreshTier(group.getTier());
        }
    }

    /**
     * Fills every knock-out fixture of the tier from its sources, round by round so a chain (semi-final →
     * final) settles in one pass. Only sides not set by hand, only fixtures without a result.
     */
    @Transactional
    public void refreshTier(Tier tier) {
        if (tier == null) return;
        Map<String, List<StandingDto>> tables = new HashMap<>();
        for (Group group : groupRepository.findByTierId(tier.getId())) {
            if (group.getKind() != GroupKind.KNOCKOUT) continue;
            List<MatchDay> fixtures = new ArrayList<>(matchDayRepository.findByRoundGroupId(group.getId()));
            fixtures.sort(Comparator.comparing((MatchDay m) -> m.getRound().getIndex())
                .thenComparing(MatchDay::getStartDate));
            for (MatchDay fixture : fixtures) {
                if (fixture.getResultState() != ResultState.OPEN) continue;
                boolean changed = false;
                if (!fixture.isHomeSetByHand() && fixture.getHomeSource() != null) {
                    Team team = resolve(fixture.getHomeSource(), tables);
                    if (!sameTeam(team, fixture.getTeamHome())) {
                        fixture.setTeamHome(team);
                        changed = true;
                    }
                }
                if (!fixture.isAwaySetByHand() && fixture.getAwaySource() != null) {
                    Team team = resolve(fixture.getAwaySource(), tables);
                    if (!sameTeam(team, fixture.getTeamAway())) {
                        fixture.setTeamAway(team);
                        changed = true;
                    }
                }
                if (changed) {
                    matchDayRepository.save(fixture);
                }
            }
        }
    }

    /**
     * Refuses an admin's correction of a final fixture whose outcome already fed a later fixture with a
     * result -- through its winner/loser, or (in a round robin) through its group's places.
     */
    @Transactional(readOnly = true)
    public void requireDownstreamUnplayed(MatchDay matchDay) {
        List<MatchDay> downstream = new ArrayList<>(
            matchDayRepository.findByHomeSource_MatchDay_IdOrAwaySource_MatchDay_Id(matchDay.getId(), matchDay.getId()));
        Group group = matchDay.getRound() == null ? null : matchDay.getRound().getGroup();
        if (group != null) {
            downstream.addAll(matchDayRepository.findByHomeSource_Group_IdOrAwaySource_Group_Id(group.getId(), group.getId()));
        }
        if (downstream.stream().anyMatch(later -> later.getResultState() != ResultState.OPEN)) {
            throw new KnockoutDownstreamPlayedException(
                "A later knock-out fixture fed by this result has a result already; correct that one first");
        }
    }

    /** Whether a knock-out fixture's result is level -- it can't become final then (docs/34). */
    @Transactional(readOnly = true)
    public boolean isLevelKnockout(MatchDay matchDay) {
        Group group = matchDay.getRound() == null ? null : matchDay.getRound().getGroup();
        if (group == null || group.getKind() != GroupKind.KNOCKOUT) return false;
        FixtureScoreService.FixtureScore score = scoreService.scores(List.of(matchDay)).get(matchDay.getId());
        return score != null && score.home() != null && Objects.equals(score.home(), score.away());
    }

    /** Edits a knock-out fixture's sources, places and team overrides; refused once it has a result. */
    @Transactional
    public MatchDayDto update(String matchDayId, KnockoutFixtureRequest request) {
        MatchDay fixture = matchDayRepository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        Group group = fixture.getRound() == null ? null : fixture.getRound().getGroup();
        if (group == null || group.getKind() != GroupKind.KNOCKOUT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only fixtures of a knock-out stage have sources");
        }
        if (fixture.getResultState() != ResultState.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The fixture has a result; its teams and sources can no longer change");
        }
        fixture.setHomeSource(toSource(request.getHomeSource(), fixture));
        fixture.setAwaySource(toSource(request.getAwaySource(), fixture));
        fixture.setWinnerPlace(request.getWinnerPlace());
        fixture.setLoserPlace(request.getLoserPlace());
        fixture.setHomeSetByHand(request.getHomeTeamId() != null);
        fixture.setAwaySetByHand(request.getAwayTeamId() != null);
        if (request.getHomeTeamId() != null) fixture.setTeamHome(leagueTeam(request.getHomeTeamId(), group));
        if (request.getAwayTeamId() != null) fixture.setTeamAway(leagueTeam(request.getAwayTeamId(), group));
        matchDayRepository.save(fixture);
        refreshTier(group.getTier());
        return matchDayMapper.toDto(matchDayRepository.findById(matchDayId).orElseThrow());
    }

    private Team resolve(FixtureSource source, Map<String, List<StandingDto>> tables) {
        if (source.getType() == null) return null;
        if (source.getType() == FixtureSourceType.GROUP_PLACE) {
            Group group = source.getGroup();
            if (group == null || source.getPlace() == null || !isFinished(group)) return null;
            List<StandingDto> table = tables.computeIfAbsent(group.getId(), standingService::getByGroup);
            return table.stream()
                .filter(row -> Objects.equals(row.getPlace(), source.getPlace()))
                .findFirst()
                .flatMap(row -> teamRepository.findById(row.getTeamId()))
                .orElse(null);
        }
        MatchDay from = source.getMatchDay();
        if (from == null || from.getResultState() != ResultState.CONFIRMED
            || from.getTeamHome() == null || from.getTeamAway() == null) {
            return null;
        }
        FixtureScoreService.FixtureScore score = scoreService.scores(List.of(from)).get(from.getId());
        if (score == null || score.home() == null || score.away() == null || score.home().equals(score.away())) {
            return null;
        }
        boolean homeWon = score.home() > score.away();
        boolean wantWinner = source.getType() == FixtureSourceType.WINNER;
        return homeWon == wantWinner ? from.getTeamHome() : from.getTeamAway();
    }

    /** A round robin is finished once it has fixtures and every one of them (byes aside) is final. */
    private boolean isFinished(Group group) {
        return group.getKind() == GroupKind.ROUND_ROBIN
            && !matchDayRepository.findByRoundGroupId(group.getId()).isEmpty()
            && !matchDayRepository.existsByRound_Group_IdAndResultStateNotAndByeFalse(group.getId(), ResultState.CONFIRMED);
    }

    private FixtureSource toSource(KnockoutFixtureRequest.Source request, MatchDay fixture) {
        if (request == null || request.getType() == null) return null;
        return switch (request.getType()) {
            case GROUP_PLACE -> {
                if (request.getGroupId() == null || request.getPlace() == null || request.getPlace() < 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A group place needs a group and a place ≥ 1");
                }
                Group group = groupRepository.findById(request.getGroupId())
                    .orElseThrow(() -> new GroupNotFoundException(request.getGroupId()));
                if (!Objects.equals(group.getTier().getId(), fixture.getRound().getGroup().getTier().getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The source group must be in the same tier");
                }
                yield FixtureSource.groupPlace(group, request.getPlace());
            }
            case WINNER, LOSER -> {
                if (request.getMatchDayId() == null || request.getMatchDayId().equals(fixture.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A winner/loser source needs another fixture");
                }
                MatchDay from = matchDayRepository.findById(request.getMatchDayId())
                    .orElseThrow(() -> new MatchDayNotFoundException(request.getMatchDayId()));
                if (!Objects.equals(from.getRound().getGroup().getId(), fixture.getRound().getGroup().getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A winner/loser source must be a fixture of the same knock-out stage");
                }
                yield request.getType() == FixtureSourceType.WINNER ? FixtureSource.winnerOf(from) : FixtureSource.loserOf(from);
            }
        };
    }

    /** A team set by hand must take part in the fixture's league. */
    private Team leagueTeam(String teamId, Group group) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> new TeamNotFoundException(teamId));
        String leagueId = group.getTier().getLeague().getId();
        boolean inLeague = participationRepository.findVisibleByLeagueId(leagueId).stream()
            .anyMatch(participation -> participation.getTeam().getId().equals(team.getId()));
        if (!inLeague) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The team doesn't take part in this league");
        }
        return team;
    }

    private static boolean sameTeam(Team a, Team b) {
        return a == null ? b == null : b != null && a.getId().equals(b.getId());
    }
}
