package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.group.GroupState;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.leaguerules.FixtureMode;
import de.dtfb.sportshub.backend.leaguerules.GamePlanEntry;
import de.dtfb.sportshub.backend.leaguerules.GamePlanEntryRepository;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import de.dtfb.sportshub.backend.leaguerules.MatchdayDecision;
import de.dtfb.sportshub.backend.leaguerules.PlaySystem;
import de.dtfb.sportshub.backend.lineup.Lineup;
import de.dtfb.sportshub.backend.lineup.LineupEntry;
import de.dtfb.sportshub.backend.lineup.LineupEntryRepository;
import de.dtfb.sportshub.backend.lineup.LineupRepository;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.match.MatchState;
import de.dtfb.sportshub.backend.match.MatchType;
import de.dtfb.sportshub.backend.match.Winner;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.matchday.SchedulingState;
import de.dtfb.sportshub.backend.matchset.MatchSet;
import de.dtfb.sportshub.backend.matchset.MatchSetRepository;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.roster.RosterEntry;
import de.dtfb.sportshub.backend.roster.RosterEntryRepository;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.round.RoundRepository;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import de.dtfb.sportshub.backend.standing.OfficialTableEntry;
import de.dtfb.sportshub.backend.standing.OfficialTableEntryRepository;
import de.dtfb.sportshub.backend.standing.StandingService;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import de.dtfb.sportshub.backend.teamparticipation.ParticipationStatus;
import de.dtfb.sportshub.backend.teamparticipation.RosterStatus;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierRepository;
import de.dtfb.sportshub.backend.util.IdGenerator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes past seasons as final history (docs/29) -- straight into the repositories, since every live
 * service refuses the past (registration into an ended season, frozen rules, today's club membership,
 * results by a logged-in user stamped "now"). Keeps the model's invariants: a team's season is its
 * league's season, every league owns one rule-set snapshot with its game plan, games follow that plan,
 * a played fixture is CONFIRMED with {@code firstFinalAt} set (final: admin-only, docs/17). The source's
 * final table becomes the group's frozen official table; the stored standings cache is rebuilt at the end.
 */
@Component
class HistoricalImportWriter {

    private final FederationRepository federationRepository;
    private final SeasonRepository seasonRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueRuleSetRepository ruleSetRepository;
    private final GamePlanEntryRepository gamePlanRepository;
    private final TierRepository tierRepository;
    private final GroupRepository groupRepository;
    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final TeamParticipationRepository participationRepository;
    private final OfficialTableEntryRepository officialTableRepository;
    private final PlayerRepository playerRepository;
    private final RosterEntryRepository rosterRepository;
    private final RoundRepository roundRepository;
    private final MatchDayRepository matchDayRepository;
    private final MatchRepository matchRepository;
    private final MatchSetRepository matchSetRepository;
    private final LineupRepository lineupRepository;
    private final LineupEntryRepository lineupEntryRepository;
    private final StandingService standingService;

    HistoricalImportWriter(FederationRepository federationRepository, SeasonRepository seasonRepository,
                           LeagueRepository leagueRepository, LeagueRuleSetRepository ruleSetRepository,
                           GamePlanEntryRepository gamePlanRepository, TierRepository tierRepository,
                           GroupRepository groupRepository, ClubRepository clubRepository, TeamRepository teamRepository,
                           TeamParticipationRepository participationRepository,
                           OfficialTableEntryRepository officialTableRepository, PlayerRepository playerRepository,
                           RosterEntryRepository rosterRepository, RoundRepository roundRepository,
                           MatchDayRepository matchDayRepository, MatchRepository matchRepository,
                           MatchSetRepository matchSetRepository, LineupRepository lineupRepository,
                           LineupEntryRepository lineupEntryRepository, StandingService standingService) {
        this.federationRepository = federationRepository;
        this.seasonRepository = seasonRepository;
        this.leagueRepository = leagueRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.gamePlanRepository = gamePlanRepository;
        this.tierRepository = tierRepository;
        this.groupRepository = groupRepository;
        this.clubRepository = clubRepository;
        this.teamRepository = teamRepository;
        this.participationRepository = participationRepository;
        this.officialTableRepository = officialTableRepository;
        this.playerRepository = playerRepository;
        this.rosterRepository = rosterRepository;
        this.roundRepository = roundRepository;
        this.matchDayRepository = matchDayRepository;
        this.matchRepository = matchRepository;
        this.matchSetRepository = matchSetRepository;
        this.lineupRepository = lineupRepository;
        this.lineupEntryRepository = lineupEntryRepository;
        this.standingService = standingService;
    }

    Apply start(ImportRun run, List<PlannedItem> plan, WrittenIds ids) {
        return new Apply(run, plan, ids);
    }

    /** One apply of past seasons. */
    final class Apply {
        private final ImportRun run;
        private final WrittenIds ids;
        private final Map<String, ImportedLeague> leagues = new HashMap<>();
        private final Map<String, ImportedTeam> teams = new HashMap<>();
        /** {@code batch:} links → the identity created for the first record of the group. */
        private final Map<String, String> batchIdentities = new HashMap<>();
        private final Map<String, Group> groupsByLeagueId = new HashMap<>();
        private final Map<String, Group> touchedGroups = new LinkedHashMap<>();
        // Kept in memory instead of one query per record: every query inside this one big transaction makes
        // Hibernate check all loaded objects first, which turns a season's import quadratic.
        private final Map<String, Map<Integer, Round>> roundsByGroup = new HashMap<>();
        private final Map<String, TeamParticipation> participationsByTeamId = new HashMap<>();

        Apply(ImportRun run, List<PlannedItem> plan, WrittenIds ids) {
            this.run = run;
            this.ids = ids;
            for (PlannedItem item : plan) {
                if (item.payload() instanceof ImportedLeague league) leagues.put(league.externalId(), league);
                if (item.payload() instanceof ImportedTeam team) teams.put(team.externalId(), team);
            }
        }

        void write(PlannedItem item) {
            if (item.action() == ImportAction.REJECTED || item.action() == ImportAction.CONFLICT) {
                return;
            }
            switch (item.recordType()) {
                case SEASON -> season(item);
                case LEAGUE -> league(item);
                case TEAM -> team(item);
                case ROSTER_ENTRY -> rosterEntry(item);
                case FIXTURE -> fixture(item);
                default -> { }
            }
        }

        /** Rebuilds the stored standings cache of every group this apply touched (delete guards read it). */
        void finish() {
            touchedGroups.values().forEach(standingService::recompute);
        }

        //region seasons, leagues
        private void season(PlannedItem item) {
            ImportedSeason source = (ImportedSeason) item.payload();
            Season season = item.action() == ImportAction.NEW ? new Season()
                : seasonRepository.findById(item.targetEntityId()).orElseThrow();
            if (item.action() == ImportAction.NEW) {
                season.setFederation(federationRepository.getReferenceById(run.getTargetFederationId()));
            }
            if (item.action() != ImportAction.UNCHANGED) {
                season.setName(source.name());
                season.setStartDate(source.startDate());
                season.setEndDate(source.endDate());
                seasonRepository.save(season);
            }
            ids.link(ImportRecordType.SEASON, item.externalId(), season.getId());
        }

        private void league(PlannedItem item) {
            ImportedLeague source = (ImportedLeague) item.payload();
            League league;
            if (item.action() == ImportAction.NEW) {
                Season season = seasonRepository.findById(ids.id(ImportRecordType.SEASON, source.seasonExternalId()))
                    .orElseThrow();
                league = new League();
                league.setLeagueIdentityId(identity(item.linkId()));
                league.setSeason(season);
                league.setName(source.name());
                league.setRuleSet(ruleSet(source, season));
                leagueRepository.save(league);

                Tier tier = new Tier();
                tier.setLeague(league);
                tier.setName(source.name());
                tier.setLevel(1);
                tierRepository.save(tier);
                Group group = new Group();
                group.setTier(tier);
                group.setName(source.name());
                group.setGroupState(GroupState.FINISHED);
                groupRepository.save(group);
                groupsByLeagueId.put(league.getId(), group);
            } else {
                league = leagueRepository.findById(item.targetEntityId()).orElseThrow();
                if (item.action() == ImportAction.UPDATE) {
                    league.setName(source.name());
                    leagueRepository.save(league);
                }
            }
            ids.link(ImportRecordType.LEAGUE, item.externalId(), league.getId());
        }

        /** The league's own snapshot (docs/21) from the source's game mode and table rule. */
        private LeagueRuleSet ruleSet(ImportedLeague source, Season season) {
            int[] points = HistoricalPlanner.points(source.tableRule());
            ImportedGameMode mode = source.mode();
            LeagueRuleSet rules = new LeagueRuleSet();
            rules.setSnapshot(true);
            rules.setFederation(season.getFederation());
            rules.setName(source.name());
            // Table rules 21-29 are the SM's Swiss-system leagues (ordered by Buchholz).
            rules.setPlaySystem(source.tableRule() >= 21 && source.tableRule() <= 29 ? PlaySystem.SWISS : PlaySystem.ROUND_ROBIN);
            boolean race = mode != null && mode.raceTarget() != null;
            rules.setFixtureMode(race ? FixtureMode.RACE : FixtureMode.GAMES);
            if (race) {
                rules.setRaceTarget(mode.raceTarget());
            }
            rules.setPointsWin(points[0]);
            rules.setPointsDraw(points[1]);
            rules.setPointsLoss(points[2]);
            rules.setMatchdayDecision(MatchdayDecision.ALL_GAMES);
            rules.setLineupRequired(false);
            ruleSetRepository.save(rules);
            if (mode != null) {
                for (int i = 0; i < mode.gamePlan().size(); i++) {
                    GamePlanEntry entry = new GamePlanEntry();
                    entry.setRuleSet(rules);
                    entry.setPosition(i + 1);
                    entry.setGameType(mode.gamePlan().get(i));
                    gamePlanRepository.save(entry);
                }
            }
            return rules;
        }

        /** The identity a NEW league/team gets: an existing one, the shared one of a {@code batch:} group, or a new one. */
        private String identity(String link) {
            if (link == null) return null;
            if (!link.startsWith(HistoricalPlanner.BATCH_LINK)) return link;
            return batchIdentities.computeIfAbsent(link, k -> IdGenerator.newId());
        }

        private Group groupOf(String leagueId) {
            return groupsByLeagueId.computeIfAbsent(leagueId,
                id -> groupRepository.findByTier_League_Id(id).stream().findFirst().orElseThrow());
        }
        //endregion

        //region teams, rosters
        private void team(PlannedItem item) {
            ImportedTeam source = (ImportedTeam) item.payload();
            League league = leagueRepository.findById(ids.id(ImportRecordType.LEAGUE, source.leagueExternalId()))
                .orElseThrow();
            Group group = groupOf(league.getId());
            Team team;
            if (item.action() == ImportAction.NEW) {
                team = new Team();
                team.setSeason(league.getSeason());
                // Hobby teams without a club stay without one (docs/29).
                String clubId = source.clubExternalId() == null ? null : ids.id(ImportRecordType.CLUB, source.clubExternalId());
                team.setClub(clubId == null ? null : clubRepository.getReferenceById(clubId));
                team.setName(source.name());
                team.setTeamIdentityId(identity(item.linkId()));
                teamRepository.save(team);

                TeamParticipation participation = new TeamParticipation();
                participation.setTeam(team);
                participation.setLeague(league);
                participation.setGroup(group);
                participation.setRosterStatus(RosterStatus.CONFIRMED);
                participation.setStatus(ParticipationStatus.ACTIVE);
                participationRepository.save(participation);
                participationsByTeamId.put(team.getId(), participation);
                officialRow(group, team, source, true);
            } else {
                team = teamRepository.findById(item.targetEntityId()).orElseThrow();
                if (item.action() == ImportAction.UPDATE) {
                    team.setName(source.name());
                    teamRepository.save(team);
                }
                officialRow(group, team, source, false);
            }
            touchedGroups.put(group.getId(), group);
            ids.link(ImportRecordType.TEAM, item.externalId(), team.getId());
            ids.link(ImportRecordType.TEAM_IDENTITY, source.identityExternalId(), team.getTeamIdentityId());
        }

        /** The team's row of the frozen official table, from the source's final table (docs/29). */
        private void officialRow(Group group, Team team, ImportedTeam source, boolean newTeam) {
            ImportedTeam.TableRow table = source.table();
            if (table == null) return;
            OfficialTableEntry row = newTeam ? new OfficialTableEntry()
                : officialTableRepository.findByGroupId(group.getId()).stream()
                    .filter(e -> e.getTeam().getId().equals(team.getId())).findFirst().orElseGet(OfficialTableEntry::new);
            row.setGroup(group);
            row.setTeam(team);
            row.setPlace(table.place());
            row.setWon(table.won());
            row.setDrawn(table.drawn());
            row.setLost(table.lost());
            row.setPlayed(table.won() + table.drawn() + table.lost());
            row.setGoalsFor(table.goalsFor());
            row.setGoalsAgainst(table.goalsAgainst());
            row.setPoints((int) Math.round(Objects.requireNonNullElse(table.points(), 0.0)));
            row.setPointsAdjustment((int) Math.round(Objects.requireNonNullElse(table.adjustment(), 0.0)));
            row.setSource(run.getSource());
            officialTableRepository.save(row);
        }

        private void rosterEntry(PlannedItem item) {
            ImportedRosterEntry source = (ImportedRosterEntry) item.payload();
            RosterEntry entry;
            if (item.action() == ImportAction.NEW) {
                Team team = teamRepository.findById(ids.id(ImportRecordType.TEAM, source.teamExternalId())).orElseThrow();
                ImportedTeam importedTeam = teams.get(source.teamExternalId());
                String leagueId = importedTeam == null ? null : ids.id(ImportRecordType.LEAGUE, importedTeam.leagueExternalId());
                TeamParticipation participation = participationsByTeamId.computeIfAbsent(team.getId(), teamId ->
                    (leagueId == null
                        ? participationRepository.findVisibleByTeamId(teamId).stream().findFirst()
                        : participationRepository.findFirstByTeam_IdAndLeague_Id(teamId, leagueId)).orElseThrow());
                entry = new RosterEntry();
                entry.setParticipation(participation);
                entry.setPlayer(playerRepository.getReferenceById(ids.id(ImportRecordType.PLAYER, source.playerExternalId())));
                entry.setAddedAt(HistoricalPlanner.startOf(team.getSeason().getStartDate()));
            } else {
                entry = rosterRepository.findById(item.targetEntityId()).orElseThrow();
            }
            if (item.action() != ImportAction.UNCHANGED) {
                Season season = entry.getParticipation().getTeam().getSeason();
                entry.setRemovedAt(source.left() ? HistoricalPlanner.startOf(season.getEndDate()) : null);
                if (entry.getAddedAt() == null) entry.setAddedAt(Instant.EPOCH);
                rosterRepository.save(entry);
            }
            ids.link(ImportRecordType.ROSTER_ENTRY, item.externalId(), entry.getId());
        }
        //endregion

        //region fixtures
        private void fixture(PlannedItem item) {
            ImportedFixture source = (ImportedFixture) item.payload();
            Team home = teamRepository.findById(ids.id(ImportRecordType.TEAM, source.homeTeamExternalId())).orElseThrow();
            Team away = teamRepository.findById(ids.id(ImportRecordType.TEAM, source.awayTeamExternalId())).orElseThrow();
            ImportedTeam importedHome = teams.get(source.homeTeamExternalId());
            ImportedLeague importedLeague = importedHome == null ? null : leagues.get(importedHome.leagueExternalId());
            String leagueId = importedHome == null ? null : ids.id(ImportRecordType.LEAGUE, importedHome.leagueExternalId());
            Group group = leagueId != null ? groupOf(leagueId)
                : participationRepository.findVisibleByTeamId(home.getId()).getFirst().getGroup();
            touchedGroups.put(group.getId(), group);

            MatchDay matchDay;
            if (item.action() == ImportAction.UNCHANGED) {
                ids.link(ImportRecordType.FIXTURE, item.externalId(), item.targetEntityId());
                return;
            }
            if (item.action() == ImportAction.NEW) {
                matchDay = new MatchDay();
                matchDay.setRound(round(group, source));
                matchDay.setTeamHome(home);
                matchDay.setTeamAway(away);
            } else {
                matchDay = matchDayRepository.findById(item.targetEntityId()).orElseThrow();
                clearGames(matchDay);
            }
            Instant kickOff = source.kickOff() != null ? source.kickOff()
                : HistoricalPlanner.startOf(home.getSeason().getStartDate());
            matchDay.setStartDate(kickOff);
            // The date is history, not a proposal: fixed, like an admin-set date.
            matchDay.setSchedulingState(SchedulingState.CONFIRMED);
            matchDay.setScheduleConfirmedAt(kickOff);
            ResultState state = !source.played() ? ResultState.OPEN
                : source.unconfirmed() ? ResultState.SUBMITTED : ResultState.CONFIRMED;
            matchDay.setResultState(state);
            Instant finalAt = state == ResultState.CONFIRMED ? kickOff : null;
            matchDay.setDecidedAt(finalAt);
            matchDay.setFirstFinalAt(finalAt);
            matchDay.setHomeConfirmedAt(finalAt);
            matchDay.setAwayConfirmedAt(finalAt);
            matchDayRepository.save(matchDay);
            games(matchDay, source, importedLeague, kickOff);
            ids.link(ImportRecordType.FIXTURE, item.externalId(), matchDay.getId());
        }

        /** One round per source matchday, named after its title, else "Spieltag N". */
        private Round round(Group group, ImportedFixture source) {
            int index = source.matchday() != null ? source.matchday() : 0;
            Map<Integer, Round> rounds = roundsByGroup.computeIfAbsent(group.getId(), groupId -> {
                Map<Integer, Round> existing = new HashMap<>();
                roundRepository.findByGroupIdOrderByIndex(groupId).forEach(r -> existing.putIfAbsent(r.getIndex(), r));
                return existing;
            });
            return rounds.computeIfAbsent(index, i -> {
                Round round = new Round();
                round.setGroup(group);
                round.setIndex(i);
                round.setName(source.matchdayTitle() != null ? source.matchdayTitle() : "Spieltag " + i);
                return roundRepository.save(round);
            });
        }

        /**
         * The fixture's games with sets and line-ups. In a RACE league (docs/22) a game's score is the running
         * score at its end, so the source's per-game goals are added up.
         */
        private void games(MatchDay matchDay, ImportedFixture source, ImportedLeague league, Instant kickOff) {
            List<MatchType> plan = league != null && league.mode() != null ? league.mode().gamePlan() : List.of();
            boolean race = league != null && league.mode() != null && league.mode().raceTarget() != null;
            Map<String, List<LineupEntry>> lineups = new LinkedHashMap<>();
            int runningHome = 0;
            int runningAway = 0;
            for (ImportedFixture.ImportedGame game : source.games()) {
                Match match = new Match();
                match.setMatchDay(matchDay);
                match.setPosition(game.number());
                match.setStartTime(kickOff);
                match.setType(game.number() >= 1 && game.number() <= plan.size() ? plan.get(game.number() - 1) : null);
                Integer homeScore = game.homeScore();
                Integer awayScore = game.awayScore();
                if (race && homeScore != null && awayScore != null) {
                    runningHome += homeScore;
                    runningAway += awayScore;
                    homeScore = runningHome;
                    awayScore = runningAway;
                }
                match.setHomeScore(homeScore);
                match.setAwayScore(awayScore);
                match.setState(source.played() ? MatchState.PLAYED : MatchState.PLANNED);
                match.setWinner(winner(game));
                matchRepository.save(match);
                for (int i = 0; i < game.sets().size(); i++) {
                    MatchSet set = new MatchSet();
                    set.setMatch(match);
                    set.setSetNumber(i + 1);
                    set.setHomeScore(game.sets().get(i)[0]);
                    set.setAwayScore(game.sets().get(i)[1]);
                    matchSetRepository.save(set);
                }
                slot(lineups, matchDay.getTeamHome().getId(), match, 1, game.homePlayer1());
                slot(lineups, matchDay.getTeamHome().getId(), match, 2, game.homePlayer2());
                slot(lineups, matchDay.getTeamAway().getId(), match, 1, game.awayPlayer1());
                slot(lineups, matchDay.getTeamAway().getId(), match, 2, game.awayPlayer2());
            }
            for (Map.Entry<String, List<LineupEntry>> side : lineups.entrySet()) {
                Lineup lineup = new Lineup();
                lineup.setMatchDay(matchDay);
                lineup.setTeam(side.getKey().equals(matchDay.getTeamHome().getId()) ? matchDay.getTeamHome() : matchDay.getTeamAway());
                lineup.setSubmittedAt(kickOff);
                lineupRepository.save(lineup);
                side.getValue().forEach(entry -> entry.setLineup(lineup));
                lineupEntryRepository.saveAll(side.getValue());
            }
        }

        private void slot(Map<String, List<LineupEntry>> lineups, String teamId, Match match, int slot, String playerExternalId) {
            String playerId = playerExternalId == null ? null : ids.id(ImportRecordType.PLAYER, playerExternalId);
            if (playerId == null) return;
            LineupEntry entry = new LineupEntry();
            entry.setMatch(match);
            entry.setSlot(slot);
            entry.setPlayer(playerRepository.getReferenceById(playerId));
            lineups.computeIfAbsent(teamId, k -> new ArrayList<>()).add(entry);
        }

        private static Winner winner(ImportedFixture.ImportedGame game) {
            Integer home = game.homeGamePoints() != null ? game.homeGamePoints() : game.homeScore();
            Integer away = game.awayGamePoints() != null ? game.awayGamePoints() : game.awayScore();
            if (home == null || away == null) return null;
            int compared = Integer.compare(home, away);
            return compared > 0 ? Winner.HOME : compared < 0 ? Winner.AWAY : Winner.DRAW;
        }

        /** A re-run replaces a fixture's games, sets and line-ups as a whole. */
        private void clearGames(MatchDay matchDay) {
            for (Lineup lineup : lineupRepository.findByMatchDay(matchDay)) {
                lineupEntryRepository.deleteByLineup(lineup);
                lineupRepository.delete(lineup);
            }
            for (Match match : matchRepository.findByMatchDay(matchDay)) {
                matchSetRepository.deleteAll(matchSetRepository.findByMatch(match));
                matchRepository.delete(match);
            }
            matchRepository.flush();
        }
        //endregion
    }
}
