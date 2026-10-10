package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.roster.RosterEntry;
import de.dtfb.sportshub.backend.roster.RosterEntryRepository;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Plans the past seasons of a batch (docs/29): seasons, team leagues, teams with their final table, rosters
 * and fixtures with games. Only seasons that have ended are taken -- running ones belong to the live
 * workflow. Cups and knockout rounds are rejected (no playoffs yet, SPO-99). A NEW league or team joins an
 * identity: one it had in an earlier run, one the admin chose, or one suggested by name (same name across
 * seasons, or same club and name), shown as {@link ImportIssueCode#LINKED_BY_NAME}.
 */
@Component
class HistoricalPlanner {

    static final String BATCH_LINK = "batch:";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final SeasonRepository seasonRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final RosterEntryRepository rosterRepository;
    private final MatchDayRepository matchDayRepository;
    private final MatchRepository matchRepository;

    HistoricalPlanner(SeasonRepository seasonRepository, LeagueRepository leagueRepository, TeamRepository teamRepository,
                      RosterEntryRepository rosterRepository, MatchDayRepository matchDayRepository,
                      MatchRepository matchRepository) {
        this.seasonRepository = seasonRepository;
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.rosterRepository = rosterRepository;
        this.matchDayRepository = matchDayRepository;
        this.matchRepository = matchRepository;
    }

    List<PlannedItem> plan(ImportBatch batch, Map<String, PlanTarget> clubs, Map<String, PlanTarget> players,
                           Map<String, String> manualMatches,
                           BiFunction<ImportRecordType, String, ExternalReference> references) {
        if (batch.seasons().isEmpty() && batch.leagues().isEmpty()) {
            return List.of();
        }
        Planning planning = new Planning(batch, clubs, players, manualMatches, references);
        List<PlannedItem> items = new ArrayList<>();
        batch.seasons().forEach(season -> items.add(planning.season(season)));
        batch.leagues().forEach(league -> items.add(planning.league(league)));
        batch.teams().forEach(team -> items.add(planning.team(team)));
        batch.rosterEntries().forEach(entry -> items.add(planning.rosterEntry(entry)));
        batch.fixtures().forEach(fixture -> items.add(planning.fixture(fixture)));
        return items;
    }

    /** Points for a win/draw/loss from the SM's {@code tabellenwertung}; anything without a points rule: 2/1/0. */
    static int[] points(int tableRule) {
        if (tableRule <= 0) {
            return new int[]{2, 1, 0};
        }
        int rule = tableRule % 20;
        int win = rule <= 3 ? 2 : rule <= 6 ? 3 : 1;
        return new int[]{win, rule <= 6 ? 1 : 0, 0};
    }

    /** A league is a cup or knockout bracket when its table rule is negative -- except -2, a table placed by hand. */
    static boolean isCupOrKnockout(ImportedLeague league) {
        return league.tableRule() < 0 && league.tableRule() != -2;
    }

    /** A league's name without years ("Landesliga Nord 2019/20" → "landesliga nord"), for linking across seasons. */
    static String identityName(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT)
            .replaceAll("\\b(19|20)\\d{2}(\\s*[/-]\\s*\\d{2,4})?\\b", " ")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim();
    }

    /** One plan: the batch's records by id and the lookups, loaded once. */
    private final class Planning {
        private final Map<String, PlanTarget> clubs;
        private final Map<String, PlanTarget> players;
        private final Map<String, String> manualMatches;
        private final BiFunction<ImportRecordType, String, ExternalReference> references;
        private final LocalDate today = LocalDate.now(ZONE);

        private final Map<String, ImportedSeason> seasonsById = new HashMap<>();
        private final Map<String, ImportedLeague> leaguesById = new HashMap<>();
        private final Map<String, ImportedTeam> teamsById = new HashMap<>();
        private final Map<String, String> playerLabels = new HashMap<>();
        private final Map<String, PlanTarget> seasonTargets = new HashMap<>();
        private final Map<String, PlanTarget> leagueTargets = new HashMap<>();
        private final Map<String, PlanTarget> teamTargets = new HashMap<>();
        private final Map<String, List<ImportedFixture>> fixturesByLeague = new HashMap<>();

        private Map<String, League> latestLeagueByName;
        private Map<String, Team> latestTeamByClubAndName;

        Planning(ImportBatch batch, Map<String, PlanTarget> clubs, Map<String, PlanTarget> players,
                 Map<String, String> manualMatches, BiFunction<ImportRecordType, String, ExternalReference> references) {
            this.clubs = clubs;
            this.players = players;
            this.manualMatches = manualMatches;
            this.references = references;
            batch.seasons().forEach(s -> seasonsById.put(s.externalId(), s));
            batch.leagues().forEach(l -> leaguesById.put(l.externalId(), l));
            batch.teams().forEach(t -> teamsById.put(t.externalId(), t));
            batch.players().forEach(p -> playerLabels.put(p.externalId(),
                (Objects.toString(p.firstName(), "") + " " + Objects.toString(p.lastName(), "")).trim()));
            for (ImportedFixture fixture : batch.fixtures()) {
                ImportedTeam home = teamsById.get(fixture.homeTeamExternalId());
                if (home != null) {
                    fixturesByLeague.computeIfAbsent(home.leagueExternalId(), k -> new ArrayList<>()).add(fixture);
                }
            }
        }

        //region seasons
        PlannedItem season(ImportedSeason source) {
            String label = source.name();
            if (source.externalId() == null) {
                return rejected(ImportRecordType.SEASON, null, label, source, ImportIssue.of(ImportIssueCode.MISSING_SOURCE_ID));
            }
            boolean hasTeamLeague = leaguesById.values().stream()
                .anyMatch(l -> source.externalId().equals(l.seasonExternalId()) && !isCupOrKnockout(l));
            if (!hasTeamLeague) {
                return remember(seasonTargets, rejected(ImportRecordType.SEASON, source.externalId(), label, source,
                    ImportIssue.of(ImportIssueCode.NO_TEAM_LEAGUES)));
            }
            if (source.endDate() == null || !source.endDate().isBefore(today)) {
                return remember(seasonTargets, rejected(ImportRecordType.SEASON, source.externalId(), label, source,
                    new ImportIssue(ImportIssueCode.SEASON_NOT_ENDED, Objects.toString(source.endDate(), ""))));
            }
            ExternalReference reference = references.apply(ImportRecordType.SEASON, source.externalId());
            Season season = reference == null ? null : seasonRepository.findById(reference.getEntityId()).orElse(null);
            if (season == null) {
                return remember(seasonTargets, item(ImportRecordType.SEASON, source.externalId(), label, ImportAction.NEW,
                    null, null, Map.of(), List.of(), source));
            }
            Map<String, FieldChange> diff = new LinkedHashMap<>();
            track(diff, "name", season.getName(), source.name());
            track(diff, "startDate", season.getStartDate(), source.startDate());
            track(diff, "endDate", season.getEndDate(), source.endDate());
            return remember(seasonTargets, item(ImportRecordType.SEASON, source.externalId(), label,
                diff.isEmpty() ? ImportAction.UNCHANGED : ImportAction.UPDATE, season.getId(), null, diff, List.of(), source));
        }
        //endregion

        //region leagues
        PlannedItem league(ImportedLeague source) {
            ImportedSeason season = seasonsById.get(source.seasonExternalId());
            String label = source.name() + (season != null && season.name() != null ? " · " + season.name() : "");
            if (source.externalId() == null) {
                return rejected(ImportRecordType.LEAGUE, null, label, source, ImportIssue.of(ImportIssueCode.MISSING_SOURCE_ID));
            }
            if (isCupOrKnockout(source)) {
                return remember(leagueTargets, rejected(ImportRecordType.LEAGUE, source.externalId(), label, source,
                    ImportIssue.of(ImportIssueCode.CUP_OR_KNOCKOUT)));
            }
            PlanTarget seasonTarget = target(seasonTargets, ImportRecordType.SEASON, source.seasonExternalId());
            if (seasonTarget == null || seasonTarget.rejected()) {
                return remember(leagueTargets, rejected(ImportRecordType.LEAGUE, source.externalId(), label, source,
                    ImportIssue.of(ImportIssueCode.BLOCKED_BY_REJECTED_RECORD)));
            }
            List<ImportIssue> issues = new ArrayList<>();
            if (source.mode() == null || source.mode().gamePlan().isEmpty()) {
                issues.add(ImportIssue.of(ImportIssueCode.NO_GAME_PLAN));
            }
            String tableDiffers = tableDifferences(source);
            if (!tableDiffers.isEmpty()) {
                issues.add(new ImportIssue(ImportIssueCode.TABLE_DIFFERS, tableDiffers));
            }

            ExternalReference reference = references.apply(ImportRecordType.LEAGUE, source.externalId());
            League league = reference == null ? null : leagueRepository.findById(reference.getEntityId()).orElse(null);
            if (league != null) {
                Map<String, FieldChange> diff = new LinkedHashMap<>();
                track(diff, "name", league.getName(), source.name());
                return remember(leagueTargets, item(ImportRecordType.LEAGUE, source.externalId(), label,
                    diff.isEmpty() ? ImportAction.UNCHANGED : ImportAction.UPDATE, league.getId(), null, diff, issues, source));
            }

            String manual = manualMatches.get(ImportService.matchKey(ImportRecordType.LEAGUE, source.externalId()));
            String link;
            String manualMatchId = null;
            if (ImportPlanner.OWN_IDENTITY.equals(manual)) {
                link = null;
                manualMatchId = manual;
                issues.add(ImportIssue.of(ImportIssueCode.MATCHED_MANUALLY));
            } else if (manual != null && !leagueRepository.findByLeagueIdentityId(manual).isEmpty()) {
                link = manual;
                manualMatchId = manual;
                issues.add(ImportIssue.of(ImportIssueCode.MATCHED_MANUALLY));
            } else {
                String name = identityName(source.name());
                League existing = name.isEmpty() ? null : latestLeagueByName().get(name);
                if (existing != null) {
                    link = existing.getLeagueIdentityId();
                    issues.add(new ImportIssue(ImportIssueCode.LINKED_BY_NAME, leagueLabel(existing)));
                } else {
                    // Leagues of the same name in this file share one new identity.
                    link = name.isEmpty() ? null : BATCH_LINK + "league:" + name;
                }
            }
            return remember(leagueTargets, new PlannedItem(ImportRecordType.LEAGUE, source.externalId(), label,
                ImportAction.NEW, null, manualMatchId, link, Map.of(), issues, source));
        }

        /** Teams whose won/drawn/lost in the source table don't match the file's played fixtures. */
        private String tableDifferences(ImportedLeague league) {
            Map<String, int[]> counted = new HashMap<>();
            for (ImportedFixture fixture : fixturesByLeague.getOrDefault(league.externalId(), List.of())) {
                if (!fixture.played() || fixture.unconfirmed() || fixture.awayTeamExternalId() == null) continue;
                int home = fixture.homeGamePoints() != null ? fixture.homeGamePoints() : fixture.homeScore();
                int away = fixture.awayGamePoints() != null ? fixture.awayGamePoints() : fixture.awayScore();
                int outcome = Integer.compare(home, away);
                count(counted, fixture.homeTeamExternalId(), outcome);
                count(counted, fixture.awayTeamExternalId(), -outcome);
            }
            return teamsById.values().stream()
                .filter(t -> league.externalId().equals(t.leagueExternalId()) && t.table() != null)
                .filter(t -> {
                    int[] c = counted.getOrDefault(t.externalId(), new int[3]);
                    return c[0] != t.table().won() || c[1] != t.table().drawn() || c[2] != t.table().lost();
                })
                .map(ImportedTeam::name)
                .sorted()
                .collect(Collectors.joining(", "));
        }

        private void count(Map<String, int[]> counted, String teamId, int outcome) {
            int[] c = counted.computeIfAbsent(teamId, k -> new int[3]);
            c[outcome > 0 ? 0 : outcome == 0 ? 1 : 2]++;
        }

        private Map<String, League> latestLeagueByName() {
            if (latestLeagueByName == null) {
                latestLeagueByName = new HashMap<>();
                leagueRepository.findAll().stream()
                    .filter(l -> l.getSeason() != null)
                    .sorted(Comparator.comparing((League l) -> l.getSeason().getStartDate(),
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                    .forEach(l -> latestLeagueByName.put(identityName(l.getName()), l));
            }
            return latestLeagueByName;
        }

        private String leagueLabel(League league) {
            return league.getName() + (league.getSeason() != null ? " · " + league.getSeason().getName() : "");
        }
        //endregion

        //region teams
        PlannedItem team(ImportedTeam source) {
            ImportedLeague league = leaguesById.get(source.leagueExternalId());
            String label = source.name() + (league != null ? " · " + league.name() : "");
            if (source.externalId() == null) {
                return rejected(ImportRecordType.TEAM, null, label, source, ImportIssue.of(ImportIssueCode.MISSING_SOURCE_ID));
            }
            PlanTarget leagueTarget = target(leagueTargets, ImportRecordType.LEAGUE, source.leagueExternalId());
            if (leagueTarget == null || leagueTarget.rejected()) {
                return remember(teamTargets, rejected(ImportRecordType.TEAM, source.externalId(), label, source,
                    ImportIssue.of(ImportIssueCode.BLOCKED_BY_REJECTED_RECORD)));
            }
            PlanTarget club = target(clubs, ImportRecordType.CLUB, source.clubExternalId());
            if (club == null || club.rejected()) {
                return remember(teamTargets, rejected(ImportRecordType.TEAM, source.externalId(), label, source,
                    new ImportIssue(ImportIssueCode.UNKNOWN_CLUB, source.clubExternalId())));
            }
            List<ImportIssue> issues = new ArrayList<>();
            if (source.table() != null && source.table().points() != null
                && source.table().points() != Math.rint(source.table().points())) {
                issues.add(new ImportIssue(ImportIssueCode.NON_INTEGER_POINTS, source.table().points().toString()));
            }

            ExternalReference reference = references.apply(ImportRecordType.TEAM, source.externalId());
            Team team = reference == null ? null : teamRepository.findById(reference.getEntityId()).orElse(null);
            if (team != null) {
                Map<String, FieldChange> diff = new LinkedHashMap<>();
                track(diff, "name", team.getName(), source.name());
                return remember(teamTargets, item(ImportRecordType.TEAM, source.externalId(), label,
                    diff.isEmpty() ? ImportAction.UNCHANGED : ImportAction.UPDATE, team.getId(), null, diff, issues, source));
            }

            String manual = manualMatches.get(ImportService.matchKey(ImportRecordType.TEAM, source.externalId()));
            ExternalReference identity = references.apply(ImportRecordType.TEAM_IDENTITY, source.identityExternalId());
            String link;
            String manualMatchId = null;
            if (ImportPlanner.OWN_IDENTITY.equals(manual)) {
                link = null;
                manualMatchId = manual;
                issues.add(ImportIssue.of(ImportIssueCode.MATCHED_MANUALLY));
            } else if (manual != null && !teamRepository.findByTeamIdentityId(manual).isEmpty()) {
                link = manual;
                manualMatchId = manual;
                issues.add(ImportIssue.of(ImportIssueCode.MATCHED_MANUALLY));
            } else if (identity != null) {
                // The source already linked this team to one imported earlier.
                link = identity.getEntityId();
            } else {
                Team existing = club.entityId() == null ? null
                    : latestTeamByClubAndName().get(club.entityId() + "|" + identityName(source.name()));
                if (existing != null) {
                    link = existing.getTeamIdentityId();
                    issues.add(new ImportIssue(ImportIssueCode.LINKED_BY_NAME, existing.getName()
                        + (existing.getSeason() != null ? " · " + existing.getSeason().getName() : "")));
                } else {
                    link = BATCH_LINK + "team:" + source.identityExternalId();
                }
            }
            return remember(teamTargets, new PlannedItem(ImportRecordType.TEAM, source.externalId(), label,
                ImportAction.NEW, null, manualMatchId, link, Map.of(), issues, source));
        }

        private Map<String, Team> latestTeamByClubAndName() {
            if (latestTeamByClubAndName == null) {
                latestTeamByClubAndName = new HashMap<>();
                teamRepository.findAll().stream()
                    .filter(t -> t.getClub() != null && t.getSeason() != null)
                    .sorted(Comparator.comparing((Team t) -> t.getSeason().getStartDate(),
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                    .forEach(t -> latestTeamByClubAndName.put(t.getClub().getId() + "|" + identityName(t.getName()), t));
            }
            return latestTeamByClubAndName;
        }
        //endregion

        //region rosters
        PlannedItem rosterEntry(ImportedRosterEntry source) {
            ImportedTeam team = teamsById.get(source.teamExternalId());
            String label = playerLabels.getOrDefault(source.playerExternalId(), source.playerExternalId()) + " → "
                + (team != null ? team.name() : source.teamExternalId());
            PlanTarget teamTarget = target(teamTargets, ImportRecordType.TEAM, source.teamExternalId());
            if (teamTarget == null || teamTarget.rejected()) {
                return rejected(ImportRecordType.ROSTER_ENTRY, source.externalId(), label, source,
                    ImportIssue.of(ImportIssueCode.BLOCKED_BY_REJECTED_RECORD));
            }
            PlanTarget player = target(players, ImportRecordType.PLAYER, source.playerExternalId());
            if (player == null || player.rejected()) {
                return rejected(ImportRecordType.ROSTER_ENTRY, source.externalId(), label, source,
                    new ImportIssue(ImportIssueCode.UNKNOWN_PLAYER, source.playerExternalId()));
            }
            ExternalReference reference = references.apply(ImportRecordType.ROSTER_ENTRY, source.externalId());
            RosterEntry entry = reference == null ? null : rosterRepository.findById(reference.getEntityId()).orElse(null);
            if (entry == null) {
                return item(ImportRecordType.ROSTER_ENTRY, source.externalId(), label, ImportAction.NEW, null, null,
                    Map.of(), List.of(), source);
            }
            Map<String, FieldChange> diff = new LinkedHashMap<>();
            track(diff, "left", entry.getRemovedAt() != null, source.left());
            return item(ImportRecordType.ROSTER_ENTRY, source.externalId(), label,
                diff.isEmpty() ? ImportAction.UNCHANGED : ImportAction.UPDATE, entry.getId(), null, diff, List.of(), source);
        }
        //endregion

        //region fixtures
        PlannedItem fixture(ImportedFixture source) {
            ImportedTeam home = teamsById.get(source.homeTeamExternalId());
            ImportedTeam away = teamsById.get(source.awayTeamExternalId());
            String label = (home != null ? home.name() : source.homeTeamExternalId()) + " – "
                + (away != null ? away.name() : Objects.toString(source.awayTeamExternalId(), "?"))
                + (source.matchdayTitle() != null ? " · " + source.matchdayTitle()
                    : source.matchday() != null ? " · " + source.matchday() : "");
            if (source.externalId() == null) {
                return rejected(ImportRecordType.FIXTURE, null, label, source, ImportIssue.of(ImportIssueCode.MISSING_SOURCE_ID));
            }
            if (source.awayTeamExternalId() == null) {
                return rejected(ImportRecordType.FIXTURE, source.externalId(), label, source, ImportIssue.of(ImportIssueCode.NO_OPPONENT));
            }
            PlanTarget homeTarget = target(teamTargets, ImportRecordType.TEAM, source.homeTeamExternalId());
            PlanTarget awayTarget = target(teamTargets, ImportRecordType.TEAM, source.awayTeamExternalId());
            if (homeTarget == null || awayTarget == null) {
                return rejected(ImportRecordType.FIXTURE, source.externalId(), label, source,
                    ImportIssue.of(ImportIssueCode.UNKNOWN_TEAM));
            }
            if (homeTarget.rejected() || awayTarget.rejected()) {
                return rejected(ImportRecordType.FIXTURE, source.externalId(), label, source,
                    ImportIssue.of(ImportIssueCode.BLOCKED_BY_REJECTED_RECORD));
            }
            List<ImportIssue> issues = new ArrayList<>();
            if (!source.played()) {
                issues.add(ImportIssue.of(ImportIssueCode.NOT_PLAYED));
            } else if (source.unconfirmed()) {
                issues.add(ImportIssue.of(ImportIssueCode.UNCONFIRMED_RESULT));
            }
            if (source.kickOff() == null) {
                issues.add(ImportIssue.of(ImportIssueCode.NO_KICKOFF));
            }
            long unknownPlayers = source.games().stream()
                .flatMap(g -> java.util.stream.Stream.of(g.homePlayer1(), g.homePlayer2(), g.awayPlayer1(), g.awayPlayer2()))
                .filter(Objects::nonNull)
                .filter(id -> { PlanTarget p = target(players, ImportRecordType.PLAYER, id); return p == null || p.rejected(); })
                .count();
            if (unknownPlayers > 0) {
                issues.add(new ImportIssue(ImportIssueCode.UNKNOWN_LINEUP_PLAYER, Long.toString(unknownPlayers)));
            }

            ExternalReference reference = references.apply(ImportRecordType.FIXTURE, source.externalId());
            MatchDay matchDay = reference == null ? null : matchDayRepository.findById(reference.getEntityId()).orElse(null);
            if (matchDay == null) {
                return item(ImportRecordType.FIXTURE, source.externalId(), label, ImportAction.NEW, null, null,
                    Map.of(), issues, source);
            }
            Map<String, FieldChange> diff = new LinkedHashMap<>();
            track(diff, "result", resultOf(matchDay), resultOf(source));
            track(diff, "kickOff", matchDay.getStartDate(), source.kickOff());
            return item(ImportRecordType.FIXTURE, source.externalId(), label,
                diff.isEmpty() ? ImportAction.UNCHANGED : ImportAction.UPDATE, matchDay.getId(), null, diff, issues, source);
        }

        /** The stored fixture as a comparable text: state and the game scores in order. */
        private String resultOf(MatchDay matchDay) {
            String games = matchRepository.findByMatchDay(matchDay).stream()
                .sorted(Comparator.comparing(Match::getPosition, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(m -> m.getHomeScore() + ":" + m.getAwayScore())
                .collect(Collectors.joining(" "));
            return matchDay.getResultState() + " " + games;
        }

        /** The source fixture in the same text form -- RACE leagues store running scores, see the writer. */
        private String resultOf(ImportedFixture fixture) {
            ImportedTeam home = teamsById.get(fixture.homeTeamExternalId());
            ImportedLeague league = home == null ? null : leaguesById.get(home.leagueExternalId());
            boolean race = league != null && league.mode() != null && league.mode().raceTarget() != null;
            ResultState state = !fixture.played() ? ResultState.OPEN
                : fixture.unconfirmed() ? ResultState.SUBMITTED : ResultState.CONFIRMED;
            int runningHome = 0;
            int runningAway = 0;
            List<String> games = new ArrayList<>();
            for (ImportedFixture.ImportedGame game : fixture.games()) {
                Integer h = game.homeScore();
                Integer a = game.awayScore();
                if (race && h != null && a != null) {
                    runningHome += h;
                    runningAway += a;
                    h = runningHome;
                    a = runningAway;
                }
                games.add(h + ":" + a);
            }
            return state + " " + String.join(" ", games);
        }
        //endregion

        //region helpers
        /** A record of this batch, or one an earlier run imported; null if neither. */
        private PlanTarget target(Map<String, PlanTarget> inBatch, ImportRecordType type, String externalId) {
            if (externalId == null) return null;
            PlanTarget target = inBatch.get(externalId);
            if (target != null) return target;
            ExternalReference reference = references.apply(type, externalId);
            return reference == null ? null : new PlanTarget(reference.getEntityId(), false);
        }

        private PlannedItem remember(Map<String, PlanTarget> targets, PlannedItem item) {
            targets.put(item.externalId(), switch (item.action()) {
                case REJECTED, CONFLICT -> PlanTarget.REJECTED;
                case NEW -> PlanTarget.CREATED;
                default -> new PlanTarget(item.targetEntityId(), false);
            });
            return item;
        }
        //endregion
    }

    private static void track(Map<String, FieldChange> diff, String field, Object current, Object source) {
        if (source == null || Objects.equals(current, source)) return;
        diff.put(field, new FieldChange(current == null ? null : current.toString(), source.toString()));
    }

    private static PlannedItem rejected(ImportRecordType type, String externalId, String label, Object payload,
                                        ImportIssue issue) {
        return item(type, externalId == null ? "" : externalId, label, ImportAction.REJECTED, null, null, Map.of(),
            List.of(issue), payload);
    }

    private static PlannedItem item(ImportRecordType type, String externalId, String label, ImportAction action,
                                    String targetEntityId, String manualMatchId, Map<String, FieldChange> diff,
                                    List<ImportIssue> issues, Object payload) {
        return new PlannedItem(type, externalId, label, action, targetEntityId, manualMatchId, diff, issues, payload);
    }

    static Instant startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZONE).toInstant();
    }
}
