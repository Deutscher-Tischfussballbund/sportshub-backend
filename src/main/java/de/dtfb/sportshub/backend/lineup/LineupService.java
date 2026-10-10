package de.dtfb.sportshub.backend.lineup;

import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
import de.dtfb.sportshub.backend.leaguerules.FixtureMode;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.match.MatchType;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayNotFoundException;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultActor;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.matchevent.MatchEvent;
import de.dtfb.sportshub.backend.matchevent.MatchEventRepository;
import de.dtfb.sportshub.backend.matchevent.MatchEventType;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.roster.RosterEntryRepository;
import de.dtfb.sportshub.backend.team.Team;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Line-ups and substitutions of a fixture (docs/23). Each team's captain (or a neutral admin) enters
 * the line-up -- the players per game, from the team's current roster -- as a draft and submits it;
 * submitting checks the rule set's line-up rules. A side is visible to its own team and to neutral
 * admins, and to everyone once both are submitted or kick-off has passed. A captain can't change their
 * line-up once it's submitted (only a neutral admin can); once both are in, substitutions ({@code MatchEvent} {@code SUBSTITUTION}) change who plays from a
 * game on, positionally. Who actually plays = the line-up with the substitutions applied in game order.
 */
@Service
public class LineupService {

    private final LineupRepository lineupRepository;
    private final LineupEntryRepository entryRepository;
    private final MatchDayRepository matchDayRepository;
    private final MatchRepository matchRepository;
    private final MatchEventRepository eventRepository;
    private final RosterEntryRepository rosterRepository;
    private final LeagueRuleResolver ruleResolver;
    private final AuthorizationService authz;

    public LineupService(LineupRepository lineupRepository, LineupEntryRepository entryRepository,
                         MatchDayRepository matchDayRepository, MatchRepository matchRepository,
                         MatchEventRepository eventRepository, RosterEntryRepository rosterRepository,
                         LeagueRuleResolver ruleResolver, AuthorizationService authz) {
        this.lineupRepository = lineupRepository;
        this.entryRepository = entryRepository;
        this.matchDayRepository = matchDayRepository;
        this.matchRepository = matchRepository;
        this.eventRepository = eventRepository;
        this.rosterRepository = rosterRepository;
        this.ruleResolver = ruleResolver;
        this.authz = authz;
    }

    // --- reads ---

    @Transactional(readOnly = true)
    public LineupsDto view(String matchDayId) {
        MatchDay matchDay = matchDayRepository.findVisibleById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        return toDto(matchDay, authz.resultActor(matchDay));
    }

    /** Whether both teams have submitted their line-up. */
    @Transactional(readOnly = true)
    public boolean bothSubmitted(MatchDay matchDay) {
        List<Lineup> lineups = lineupRepository.findByMatchDay(matchDay);
        return lineups.size() == 2 && lineups.stream().allMatch(l -> l.getSubmittedAt() != null);
    }

    /** Line-up status per fixture for lists: [home, away], with one query for all line-ups. */
    @Transactional(readOnly = true)
    public Map<String, LineupStatus[]> statuses(Collection<MatchDay> matchDays) {
        Map<String, LineupStatus[]> statuses = new HashMap<>();
        for (MatchDay matchDay : matchDays) {
            statuses.put(matchDay.getId(), new LineupStatus[] {LineupStatus.MISSING, LineupStatus.MISSING});
        }
        if (matchDays.isEmpty()) return statuses;
        for (Lineup lineup : lineupRepository.findByMatchDayIn(matchDays)) {
            MatchDay matchDay = lineup.getMatchDay();
            LineupStatus[] pair = statuses.get(matchDay.getId());
            if (pair == null) continue;
            int index = matchDay.getTeamHome() != null && matchDay.getTeamHome().getId().equals(lineup.getTeam().getId()) ? 0 : 1;
            pair[index] = lineup.getSubmittedAt() != null ? LineupStatus.SUBMITTED : LineupStatus.DRAFT;
        }
        return statuses;
    }

    /** Whether the fixture's rule set requires line-ups before a team enters a result (null = yes). */
    @Transactional(readOnly = true)
    public boolean lineupRequired(MatchDay matchDay) {
        LeagueRuleSet rules = rulesOf(matchDay);
        return !matchDay.isBye() && (rules == null || !Boolean.FALSE.equals(rules.getLineupRequired()));
    }

    /**
     * Who plays each game, per side, as the current user may see it (null for a hidden side) -- for
     * the result page.
     */
    @Transactional(readOnly = true)
    public Map<String, List<List<LineupsDto.PlayerRefDto>>> playersForResult(MatchDay matchDay, ResultActor actor) {
        LineupsDto view = toDto(matchDay, actor);
        Map<String, List<List<LineupsDto.PlayerRefDto>>> byGame = new HashMap<>();
        for (LineupsDto.GameDto game : view.getGames()) {
            List<List<LineupsDto.PlayerRefDto>> sides = new ArrayList<>();
            sides.add(game.getHomePlayers());
            sides.add(game.getAwayPlayers());
            byGame.put(game.getMatchId(), sides);
        }
        return byGame;
    }

    // --- writes ---

    /** Saves a side's line-up as a draft, or submits it (checked against the line-up rules). */
    @Transactional
    public LineupsDto save(String matchDayId, ResultActor.Side side, LineupRequests.SaveLineup request, String dtfbId) {
        MatchDay matchDay = matchDayRepository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        ResultActor actor = authz.resultActor(matchDay);
        requireNotBye(matchDay);
        Team team = teamOf(matchDay, side);
        if (!actor.neutralAdmin()) {
            if (actor.captainSide() != side) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only this team's captain can enter its line-up");
            }
            if (bothSubmitted(matchDay)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Both line-ups are submitted; from now on only substitutions change who plays");
            }
            if (lineupRepository.findByMatchDayAndTeamId(matchDay, team.getId()).map(l -> l.getSubmittedAt() != null).orElse(false)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your line-up is submitted; only the tournament management can change it now");
            }
            if (matchDay.getResultState() != ResultState.OPEN) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A result has been entered; the line-up is fixed");
            }
        }

        List<Match> games = games(matchDay);
        Map<String, Match> gameById = new HashMap<>();
        games.forEach(game -> gameById.put(game.getId(), game));
        Map<String, Player> roster = new LinkedHashMap<>();
        rosterRepository.activeRosterPlayers(team.getId(), leagueIdOf(matchDay)).forEach(p -> roster.put(p.getId(), p));

        Map<String, List<Player>> planned = new LinkedHashMap<>();
        for (LineupRequests.GameEntry entry : request.getGames() == null ? List.<LineupRequests.GameEntry>of() : request.getGames()) {
            Match game = gameById.get(entry.getMatchId());
            if (game == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A game doesn't belong to this fixture");
            }
            List<String> ids = entry.getPlayerIds() == null ? List.of() : entry.getPlayerIds().stream().filter(Objects::nonNull).toList();
            if (ids.size() > slotsOf(game) || new HashSet<>(ids).size() != ids.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Each game takes " + slotsOf(game) + " different player(s)");
            }
            List<Player> players = new ArrayList<>();
            for (String id : ids) {
                Player player = roster.get(id);
                if (player == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A player isn't on the team's current roster");
                }
                players.add(player);
            }
            planned.put(game.getId(), players);
        }
        if (request.isSubmit()) {
            String violation = violation(games, planned, rulesOf(matchDay));
            if (violation != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, violation);
            }
        }

        Lineup lineup = lineupRepository.findByMatchDayAndTeamId(matchDay, team.getId()).orElseGet(() -> {
            Lineup fresh = new Lineup();
            fresh.setMatchDay(matchDay);
            fresh.setTeam(team);
            return fresh;
        });
        lineup.setSubmittedAt(request.isSubmit() ? Instant.now() : null);
        lineup.setSubmittedByDtfbId(request.isSubmit() ? dtfbId : null);
        Lineup saved = lineupRepository.save(lineup);
        entryRepository.deleteByLineup(saved);
        entryRepository.flush();
        planned.forEach((matchId, players) -> {
            for (int i = 0; i < players.size(); i++) {
                LineupEntry entry = new LineupEntry();
                entry.setLineup(saved);
                entry.setMatch(gameById.get(matchId));
                entry.setSlot(i + 1);
                entry.setPlayer(players.get(i));
                entryRepository.save(entry);
            }
        });
        return toDto(matchDay, actor);
    }

    /** Records a substitution: from game {@code matchId} on, {@code playerIn} replaces {@code playerOut}. */
    @Transactional
    public LineupsDto substitute(String matchDayId, LineupRequests.Substitute request) {
        MatchDay matchDay = matchDayRepository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        ResultActor actor = authz.resultActor(matchDay);
        requireNotBye(matchDay);
        ResultActor.Side side = request.getSide();
        if (side == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "side is required");
        }
        if (!actor.neutralAdmin()) {
            if (actor.captainSide() != side) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only this team's captain can record its substitutions");
            }
            if (!bothSubmitted(matchDay)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Substitutions start once both line-ups are submitted");
            }
            if (matchDay.hasBeenFinal()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The result has been final");
            }
        }
        Team team = teamOf(matchDay, side);
        List<Match> games = games(matchDay);
        Match game = games.stream().filter(g -> g.getId().equals(request.getMatchId())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "The game doesn't belong to this fixture"));
        if (!actor.neutralAdmin() && game.getHomeScore() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That game already has a score; substitute in a later game");
        }

        List<MatchEvent> substitutions = substitutionsOf(matchDay, team);
        LeagueRuleSet rules = rulesOf(matchDay);
        if (rules != null && rules.getMaxSubstitutions() != null && substitutions.size() >= rules.getMaxSubstitutions()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No substitutions left (" + rules.getMaxSubstitutions() + " per fixture)");
        }
        Map<String, List<Player>> effective = effective(games, planned(matchDay, team), substitutions);
        List<Player> inGame = effective.getOrDefault(game.getId(), List.of());
        Player out = inGame.stream().filter(p -> p.getId().equals(request.getPlayerOutId())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "That player doesn't play this game"));
        Player in = rosterRepository.activeRosterPlayers(team.getId(), leagueIdOf(matchDay)).stream()
            .filter(p -> p.getId().equals(request.getPlayerInId())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "The incoming player isn't on the team's roster"));
        boolean alreadyUsed = effective.values().stream().anyMatch(players -> players.stream().anyMatch(p -> p.getId().equals(in.getId())))
            || substitutions.stream().anyMatch(s -> s.getPlayerOut() != null && s.getPlayerOut().getId().equals(in.getId()));
        if (alreadyUsed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "The incoming player has already played or is lined up for a game");
        }

        MatchEvent event = new MatchEvent();
        event.setType(MatchEventType.SUBSTITUTION);
        event.setMatch(game);
        event.setTeam(team);
        event.setPlayerIn(in);
        event.setPlayerOut(out);
        event.setTimestamp(Instant.now());
        eventRepository.save(event);
        return toDto(matchDay, actor);
    }

    /** Removes a substitution: its captain while its game has no score, a neutral admin always. */
    @Transactional
    public LineupsDto deleteSubstitution(String matchDayId, String eventId) {
        MatchDay matchDay = matchDayRepository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));
        ResultActor actor = authz.resultActor(matchDay);
        MatchEvent event = eventRepository.findById(eventId)
            .filter(e -> e.getType() == MatchEventType.SUBSTITUTION && e.getMatch() != null
                && e.getMatch().getMatchDay().getId().equals(matchDayId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Substitution not found"));
        if (!canDelete(event, matchDay, actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't remove this substitution");
        }
        eventRepository.delete(event);
        return toDto(matchDay, actor);
    }

    /** Removes a fixture's line-ups and substitutions -- before its games are deleted or rebuilt. */
    @Transactional
    public void deleteForFixture(MatchDay matchDay) {
        for (Lineup lineup : lineupRepository.findByMatchDay(matchDay)) {
            entryRepository.deleteByLineup(lineup);
            lineupRepository.delete(lineup);
        }
        for (Match game : matchRepository.findByMatchDay(matchDay)) {
            eventRepository.deleteAll(eventRepository.findByMatch(game));
        }
        entryRepository.flush();
    }

    // --- rules ---

    /**
     * Why a complete line-up breaks the rules, or null (docs/23): every game fully lined up; at most N
     * games and M singles per player, at most K players; with the block rule, the first block of
     * doubles all different players, and the rest all different with at least two from that block.
     */
    static String violation(List<Match> games, Map<String, List<Player>> planned, LeagueRuleSet rules) {
        Map<String, Integer> gamesPerPlayer = new HashMap<>();
        Map<String, Integer> singlesPerPlayer = new HashMap<>();
        for (Match game : games) {
            List<Player> players = planned.getOrDefault(game.getId(), List.of());
            if (players.size() != slotsOf(game)) {
                return "Every game needs " + slotsOf(game) + " player(s) before the line-up can be submitted";
            }
            for (Player player : players) {
                gamesPerPlayer.merge(player.getId(), 1, Integer::sum);
                if (game.getType() != MatchType.DOUBLE) {
                    singlesPerPlayer.merge(player.getId(), 1, Integer::sum);
                }
            }
        }
        if (rules == null) {
            return null;
        }
        Integer maxGames = rules.getLineupMaxGamesPerPlayer();
        if (maxGames != null && gamesPerPlayer.values().stream().anyMatch(n -> n > maxGames)) {
            return "A player may play at most " + maxGames + " games";
        }
        Integer maxSingles = rules.getLineupMaxSinglesPerPlayer();
        if (maxSingles != null && singlesPerPlayer.values().stream().anyMatch(n -> n > maxSingles)) {
            return "A player may play at most " + maxSingles + " single(s)";
        }
        Integer maxPlayers = rules.getLineupMaxPlayers();
        if (maxPlayers != null && gamesPerPlayer.size() > maxPlayers) {
            return "At most " + maxPlayers + " different players per fixture";
        }
        if (Boolean.TRUE.equals(rules.getLineupBlockRule()) && rules.getFixtureMode() == FixtureMode.RACE) {
            int firstBlockEnd = 0;
            while (firstBlockEnd < games.size() && games.get(firstBlockEnd).getType() == MatchType.DOUBLE) {
                firstBlockEnd++;
            }
            List<Player> first = new ArrayList<>();
            games.subList(0, firstBlockEnd).forEach(g -> first.addAll(planned.get(g.getId())));
            List<Player> rest = new ArrayList<>();
            games.subList(firstBlockEnd, games.size()).forEach(g -> rest.addAll(planned.get(g.getId())));
            if (distinct(first) != first.size()) {
                return "The first " + firstBlockEnd + " doubles need " + first.size() + " different players";
            }
            if (!rest.isEmpty() && distinct(rest) != rest.size()) {
                return "The remaining games need " + rest.size() + " different players";
            }
            Set<String> firstIds = new HashSet<>();
            first.forEach(p -> firstIds.add(p.getId()));
            long repeated = rest.stream().filter(p -> firstIds.contains(p.getId())).count();
            if (!rest.isEmpty() && repeated < 2) {
                return "At least two players of the first " + firstBlockEnd + " doubles must also play in the remaining games";
            }
        }
        return null;
    }

    private static long distinct(List<Player> players) {
        return players.stream().map(Player::getId).distinct().count();
    }

    // --- assembly ---

    private LineupsDto toDto(MatchDay matchDay, ResultActor actor) {
        List<Match> games = games(matchDay);
        LeagueRuleSet rules = rulesOf(matchDay);
        boolean both = bothSubmitted(matchDay);
        boolean kickOffPassed = matchDay.getStartDate() != null && Instant.now().isAfter(matchDay.getStartDate());

        LineupsDto dto = new LineupsDto();
        dto.setMatchDayId(matchDay.getId());
        dto.setLineupRequired(lineupRequired(matchDay));
        dto.setBothSubmitted(both);
        dto.setKickOffPassed(kickOffPassed);
        if (rules != null) {
            dto.setMaxGamesPerPlayer(rules.getLineupMaxGamesPerPlayer());
            dto.setMaxSinglesPerPlayer(rules.getLineupMaxSinglesPerPlayer());
            dto.setMaxPlayers(rules.getLineupMaxPlayers());
            dto.setBlockRule(Boolean.TRUE.equals(rules.getLineupBlockRule()) && rules.getFixtureMode() == FixtureMode.RACE);
            dto.setMaxSubstitutions(rules.getMaxSubstitutions());
        }
        dto.setSide(actor.memberSide());
        dto.setNeutralAdmin(actor.neutralAdmin());

        Map<ResultActor.Side, Map<String, List<Player>>> effectiveBySide = new HashMap<>();
        List<LineupsDto.SubstitutionDto> substitutionDtos = new ArrayList<>();
        for (ResultActor.Side side : ResultActor.Side.values()) {
            Team team = side == ResultActor.Side.HOME ? matchDay.getTeamHome() : matchDay.getTeamAway();
            LineupsDto.SideDto sideDto = new LineupsDto.SideDto();
            if (team == null) {
                if (side == ResultActor.Side.HOME) dto.setHome(sideDto); else dto.setAway(sideDto);
                continue;
            }
            Lineup lineup = lineupRepository.findByMatchDayAndTeamId(matchDay, team.getId()).orElse(null);
            boolean own = actor.memberSide() == side || actor.captainSide() == side;
            boolean visible = actor.neutralAdmin() || own || both || kickOffPassed;
            Map<String, List<Player>> planned = planned(matchDay, team);
            List<MatchEvent> substitutions = substitutionsOf(matchDay, team);

            sideDto.setTeamId(team.getId());
            sideDto.setTeamName(team.getName());
            sideDto.setSubmitted(lineup != null && lineup.getSubmittedAt() != null);
            sideDto.setSubmittedAt(lineup == null ? null : lineup.getSubmittedAt());
            sideDto.setVisible(visible);
            boolean captainHere = actor.captainSide() == side;
            sideDto.setCanEdit(!matchDay.isBye() && (actor.neutralAdmin()
                || (captainHere && !sideDto.isSubmitted() && matchDay.getResultState() == ResultState.OPEN)));
            sideDto.setCanSubstitute(!matchDay.isBye() && both
                && (actor.neutralAdmin() ? matchDay.getResultState() != ResultState.CONFIRMED : captainHere && !matchDay.hasBeenFinal()));
            Integer max = rules == null ? null : rules.getMaxSubstitutions();
            sideDto.setSubstitutionsLeft(max == null ? null : Math.max(0, max - substitutions.size()));
            if (visible) {
                List<LineupsDto.PlannedDto> plannedDtos = new ArrayList<>();
                for (Match game : games) {
                    LineupsDto.PlannedDto p = new LineupsDto.PlannedDto();
                    p.setMatchId(game.getId());
                    p.setPlayerIds(planned.getOrDefault(game.getId(), List.of()).stream().map(Player::getId).toList());
                    plannedDtos.add(p);
                }
                sideDto.setPlanned(plannedDtos);
                effectiveBySide.put(side, effective(games, planned, substitutions));
                for (MatchEvent s : substitutions) {
                    LineupsDto.SubstitutionDto sub = new LineupsDto.SubstitutionDto();
                    sub.setId(s.getId());
                    sub.setSide(side);
                    sub.setMatchId(s.getMatch().getId());
                    sub.setPlayerIn(ref(s.getPlayerIn()));
                    sub.setPlayerOut(ref(s.getPlayerOut()));
                    sub.setTimestamp(s.getTimestamp());
                    sub.setCanDelete(canDelete(s, matchDay, actor));
                    substitutionDtos.add(sub);
                }
            }
            if (sideDto.isCanEdit() || sideDto.isCanSubstitute()) {
                sideDto.setRoster(rosterRepository.activeRosterPlayers(team.getId(), leagueIdOf(matchDay)).stream()
                    .map(LineupService::ref).toList());
            }
            if (side == ResultActor.Side.HOME) dto.setHome(sideDto); else dto.setAway(sideDto);
        }

        List<LineupsDto.GameDto> gameDtos = new ArrayList<>();
        for (Match game : games) {
            LineupsDto.GameDto g = new LineupsDto.GameDto();
            g.setMatchId(game.getId());
            g.setPosition(game.getPosition());
            g.setType(game.getType());
            Map<String, List<Player>> home = effectiveBySide.get(ResultActor.Side.HOME);
            Map<String, List<Player>> away = effectiveBySide.get(ResultActor.Side.AWAY);
            g.setHomePlayers(home == null ? null : home.getOrDefault(game.getId(), List.of()).stream().map(LineupService::ref).toList());
            g.setAwayPlayers(away == null ? null : away.getOrDefault(game.getId(), List.of()).stream().map(LineupService::ref).toList());
            gameDtos.add(g);
        }
        dto.setGames(gameDtos);
        substitutionDtos.sort(Comparator.comparing(LineupsDto.SubstitutionDto::getTimestamp,
            Comparator.nullsLast(Comparator.naturalOrder())));
        dto.setSubstitutions(substitutionDtos);
        return dto;
    }

    /**
     * The line-up with the substitutions applied: each substitution replaces {@code playerOut} with
     * {@code playerIn} in its game and every later game where {@code playerOut} was lined up
     * (positional). Applied in game order, then time.
     */
    static Map<String, List<Player>> effective(List<Match> games, Map<String, List<Player>> planned, List<MatchEvent> substitutions) {
        Map<String, List<Player>> result = new LinkedHashMap<>();
        games.forEach(game -> result.put(game.getId(), new ArrayList<>(planned.getOrDefault(game.getId(), List.of()))));
        Map<String, Integer> positionOf = new HashMap<>();
        games.forEach(game -> positionOf.put(game.getId(), game.getPosition() == null ? 0 : game.getPosition()));
        List<MatchEvent> ordered = new ArrayList<>(substitutions);
        ordered.sort(Comparator.<MatchEvent>comparingInt(e -> positionOf.getOrDefault(e.getMatch().getId(), 0))
            .thenComparing(MatchEvent::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())));
        for (MatchEvent sub : ordered) {
            int from = positionOf.getOrDefault(sub.getMatch().getId(), 0);
            for (Match game : games) {
                if ((game.getPosition() == null ? 0 : game.getPosition()) < from) continue;
                List<Player> players = result.get(game.getId());
                for (int i = 0; i < players.size(); i++) {
                    if (players.get(i).getId().equals(sub.getPlayerOut().getId())) {
                        players.set(i, sub.getPlayerIn());
                    }
                }
            }
        }
        return result;
    }

    private boolean canDelete(MatchEvent event, MatchDay matchDay, ResultActor actor) {
        if (actor.neutralAdmin()) return true;
        ResultActor.Side side = event.getTeam() != null && matchDay.getTeamHome() != null
            && event.getTeam().getId().equals(matchDay.getTeamHome().getId()) ? ResultActor.Side.HOME : ResultActor.Side.AWAY;
        return actor.captainSide() == side && event.getMatch().getHomeScore() == null
            && !matchDay.hasBeenFinal();
    }

    private Map<String, List<Player>> planned(MatchDay matchDay, Team team) {
        Map<String, List<Player>> planned = new HashMap<>();
        lineupRepository.findByMatchDayAndTeamId(matchDay, team.getId()).ifPresent(lineup ->
            entryRepository.findByLineup(lineup).stream()
                .sorted(Comparator.comparingInt(LineupEntry::getSlot))
                .forEach(e -> planned.computeIfAbsent(e.getMatch().getId(), id -> new ArrayList<>()).add(e.getPlayer())));
        return planned;
    }

    private List<MatchEvent> substitutionsOf(MatchDay matchDay, Team team) {
        return eventRepository.findByMatch_MatchDay_IdAndType(matchDay.getId(), MatchEventType.SUBSTITUTION).stream()
            .filter(e -> e.getTeam() != null && e.getTeam().getId().equals(team.getId()))
            .toList();
    }

    private List<Match> games(MatchDay matchDay) {
        return matchRepository.findByMatchDay(matchDay).stream()
            .sorted(Comparator.comparing(Match::getPosition, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private static int slotsOf(Match game) {
        return game.getType() == MatchType.DOUBLE ? 2 : 1;
    }

    private LeagueRuleSet rulesOf(MatchDay matchDay) {
        return matchDay.getRound() == null ? null : ruleResolver.effectiveFor(matchDay.getRound().getGroup());
    }

    private static String leagueIdOf(MatchDay matchDay) {
        return matchDay.getRound() == null || matchDay.getRound().getGroup() == null
            || matchDay.getRound().getGroup().getTier() == null || matchDay.getRound().getGroup().getTier().getLeague() == null
            ? null : matchDay.getRound().getGroup().getTier().getLeague().getId();
    }

    private static Team teamOf(MatchDay matchDay, ResultActor.Side side) {
        Team team = side == ResultActor.Side.HOME ? matchDay.getTeamHome() : matchDay.getTeamAway();
        if (team == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No team on that side");
        }
        return team;
    }

    private static void requireNotBye(MatchDay matchDay) {
        if (matchDay.isBye()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A fixture against the bye has no line-ups");
        }
    }

    private static LineupsDto.PlayerRefDto ref(Player player) {
        if (player == null) return null;
        LineupsDto.PlayerRefDto ref = new LineupsDto.PlayerRefDto();
        ref.setId(player.getId());
        ref.setName((Objects.toString(player.getFirstName(), "") + " " + Objects.toString(player.getLastName(), "")).trim());
        return ref;
    }
}
