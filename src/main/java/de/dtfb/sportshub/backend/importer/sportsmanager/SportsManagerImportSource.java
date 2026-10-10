package de.dtfb.sportshub.backend.importer.sportsmanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dtfb.sportshub.backend.importer.ImportBatch;
import de.dtfb.sportshub.backend.importer.ImportFormatException;
import de.dtfb.sportshub.backend.importer.ImportRecordType;
import de.dtfb.sportshub.backend.importer.ImportSource;
import de.dtfb.sportshub.backend.importer.ImportedClub;
import de.dtfb.sportshub.backend.importer.ImportedFixture;
import de.dtfb.sportshub.backend.importer.ImportedGameMode;
import de.dtfb.sportshub.backend.importer.ImportedLeague;
import de.dtfb.sportshub.backend.importer.ImportedRosterEntry;
import de.dtfb.sportshub.backend.importer.ImportedSeason;
import de.dtfb.sportshub.backend.importer.ImportedTeam;
import de.dtfb.sportshub.backend.importer.ImportedFederation;
import de.dtfb.sportshub.backend.importer.ImportedMembership;
import de.dtfb.sportshub.backend.importer.ImportedPlayer;
import de.dtfb.sportshub.backend.match.MatchType;
import de.dtfb.sportshub.backend.player.PlayerGender;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Sports Manager's "Sports Hub export" (format {@code sportshub-sm-export}, docs/28) -- one
 * JSON file per SM installation, written by {@code com_sportsmanager}. Version 2 adds past seasons with
 * leagues, teams and their final tables, rosters, fixtures and games (docs/29). Field names are the SM's own
 * column names, so the export stays a plain dump. Normalizes silently: trims, blank → null, SM gender
 * codes, 0/1 flags. Everything else (missing values, duplicates) is the planner's business.
 */
@Component
public class SportsManagerImportSource implements ImportSource {

    public static final String KEY = "sportsmanager";
    static final String FORMAT = "sportshub-sm-export";
    /** Version 1: master data only. Version 2 adds past seasons (docs/29). */
    static final Set<Integer> VERSIONS = Set.of(1, 2);
    private static final ZoneId SM_ZONE = ZoneId.of("Europe/Berlin");
    private static final Pattern SET_SCORE = Pattern.compile("(\\d+)\\s*[:\\-]\\s*(\\d+)");

    private final ObjectMapper objectMapper;

    public SportsManagerImportSource(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Set<ImportRecordType> supports() {
        return Set.of(ImportRecordType.FEDERATION, ImportRecordType.CLUB, ImportRecordType.PLAYER,
            ImportRecordType.CLUB_MEMBERSHIP, ImportRecordType.SEASON, ImportRecordType.LEAGUE, ImportRecordType.TEAM,
            ImportRecordType.ROSTER_ENTRY, ImportRecordType.FIXTURE);
    }

    @Override
    public ImportBatch parse(InputStream in, String filename) {
        JsonNode root;
        try {
            root = objectMapper.readTree(in);
        } catch (IOException e) {
            throw new ImportFormatException("Not a JSON file", e);
        }
        if (root == null || !FORMAT.equals(text(root, "format"))) {
            throw new ImportFormatException("Not a Sports Manager export (format \"" + FORMAT + "\" expected)");
        }
        int version = root.path("version").asInt(0);
        if (!VERSIONS.contains(version)) {
            throw new ImportFormatException("Unsupported export version " + version + " (expected one of " + VERSIONS + ")");
        }
        String instance = text(root, "instance");
        if (instance == null) {
            throw new ImportFormatException("The export names no SM installation (\"instance\")");
        }

        ImportBatch.Header header = new ImportBatch.Header(KEY, instance.toLowerCase(Locale.ROOT),
            instant(text(root, "exportedAt")), version, root.path("anonymized").asBoolean(false));
        Map<String, ImportedGameMode> modes = modes(root);
        List<ImportedLeague> leagues = leagues(root, modes);
        return new ImportBatch(header, federations(root), clubs(root), players(root), memberships(root),
            seasons(root, leagues), leagues, teams(root), rosterEntries(root), fixtures(root));
    }

    //region past seasons (docs/29)
    /** The SM keeps no dates on a season: the earliest first and latest last day of its leagues. */
    private static List<ImportedSeason> seasons(JsonNode root, List<ImportedLeague> leagues) {
        List<ImportedSeason> result = new ArrayList<>();
        for (JsonNode node : root.path("saisons")) {
            String id = text(node, "saison_id");
            LocalDate start = leagues.stream().filter(l -> l.seasonExternalId() != null && l.seasonExternalId().equals(id))
                .map(ImportedLeague::firstDay).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            LocalDate end = leagues.stream().filter(l -> l.seasonExternalId() != null && l.seasonExternalId().equals(id))
                .map(ImportedLeague::lastDay).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            result.add(new ImportedSeason(id, text(node, "saisonbezeichnung"), start, end));
        }
        return result;
    }

    /** {@code modus} lists the games before {@code |}: D = doubles, E = singles, S = result only. */
    private static Map<String, ImportedGameMode> modes(JsonNode root) {
        Map<String, ImportedGameMode> result = new HashMap<>();
        for (JsonNode node : root.path("modi")) {
            String id = text(node, "teamspiel_modus_id");
            String plan = Objects.toString(text(node, "modus"), "");
            int bar = plan.indexOf('|');
            List<MatchType> games = new ArrayList<>();
            for (String token : (bar >= 0 ? plan.substring(0, bar) : plan).split(",")) {
                String game = token.trim().toUpperCase(Locale.ROOT);
                if (game.isEmpty()) continue;
                games.add(switch (game.charAt(0)) {
                    case 'D' -> MatchType.DOUBLE;
                    case 'E' -> MatchType.SINGLE;
                    default -> null;
                });
            }
            int condition = node.path("spielpunkte_bedingung").asInt(0);
            result.put(id, new ImportedGameMode(id, text(node, "bezeichnung"), games, condition > 0 ? condition : null));
        }
        return result;
    }

    private static List<ImportedLeague> leagues(JsonNode root, Map<String, ImportedGameMode> modes) {
        List<ImportedLeague> result = new ArrayList<>();
        for (JsonNode node : root.path("veranstaltungen")) {
            result.add(new ImportedLeague(text(node, "veranstaltung_id"), text(node, "saison_id"),
                text(node, "veranstalter_id"), text(node, "bezeichnung"), node.path("tabellenwertung").asInt(0),
                date(text(node, "erster_tag")), date(text(node, "letzter_tag")), modes.get(text(node, "modus_id"))));
        }
        return result;
    }

    private static List<ImportedTeam> teams(JsonNode root) {
        List<ImportedTeam> result = new ArrayList<>();
        for (JsonNode node : root.path("teams")) {
            String id = text(node, "team_id");
            String group = text(node, "teamgruppe_id");
            ImportedTeam.TableRow table = new ImportedTeam.TableRow(integer(node, "platz"), decimal(node, "gesamtpunkte"),
                decimal(node, "zusatzpunkte"), node.path("siege").asInt(0), node.path("unentschieden").asInt(0),
                node.path("niederlagen").asInt(0), node.path("spielpunkte_gewonnen").asInt(0),
                node.path("spielpunkte_verloren").asInt(0), node.path("punkte_gewonnen").asInt(0),
                node.path("punkte_verloren").asInt(0));
            result.add(new ImportedTeam(id, text(node, "veranstaltung_id"), text(node, "verein_id"),
                group != null ? group : id, text(node, "teamname"), table));
        }
        return result;
    }

    /**
     * The SM may list a player twice in one team (left, then joined again): one record per team and player,
     * active if any of its rows is -- otherwise the rows would overwrite each other on every run.
     */
    private static List<ImportedRosterEntry> rosterEntries(JsonNode root) {
        Map<String, ImportedRosterEntry> result = new LinkedHashMap<>();
        for (JsonNode node : root.path("kader")) {
            ImportedRosterEntry entry = new ImportedRosterEntry(text(node, "spieler_id"), text(node, "team_id"),
                flag(node, "ausgetreten"));
            result.merge(entry.teamExternalId() + ":" + entry.playerExternalId(), entry, (first, again) ->
                new ImportedRosterEntry(first.playerExternalId(), first.teamExternalId(), first.left() && again.left()));
        }
        return new ArrayList<>(result.values());
    }

    private static List<ImportedFixture> fixtures(JsonNode root) {
        Map<String, List<ImportedFixture.ImportedGame>> gamesByFixture = new HashMap<>();
        for (JsonNode node : root.path("teamspiele")) {
            List<int[]> sets = new ArrayList<>();
            String detail = text(node, "ergebnis_detailliert");
            if (detail != null) {
                Matcher matcher = SET_SCORE.matcher(detail);
                while (matcher.find()) {
                    sets.add(new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))});
                }
            }
            gamesByFixture.computeIfAbsent(text(node, "begegnung_id"), k -> new ArrayList<>())
                .add(new ImportedFixture.ImportedGame(node.path("teamspiel_nummer").asInt(0),
                    text(node, "heim_spieler_1_id"), text(node, "heim_spieler_2_id"), text(node, "gast_spieler_1_id"),
                    text(node, "gast_spieler_2_id"), integer(node, "teamspiel_heim_punkte"),
                    integer(node, "teamspiel_gast_punkte"), integer(node, "teamspiel_heim_spielpunkte"),
                    integer(node, "teamspiel_gast_spielpunkte"), sets));
        }
        List<ImportedFixture> result = new ArrayList<>();
        for (JsonNode node : root.path("begegnungen")) {
            String id = text(node, "begegnung_id");
            List<ImportedFixture.ImportedGame> games = gamesByFixture.getOrDefault(id, List.of()).stream()
                .sorted(Comparator.comparingInt(ImportedFixture.ImportedGame::number)).toList();
            result.add(new ImportedFixture(id, text(node, "heim_team_id"), text(node, "gast_team_id"),
                integer(node, "spieltag"), text(node, "spieltag_titel"), localDateTime(text(node, "zeitpunkt")),
                integer(node, "heim_punkte"), integer(node, "gast_punkte"), integer(node, "heim_spielpunkte"),
                integer(node, "gast_spielpunkte"), flag(node, "unbestaetigt"), games));
        }
        return result;
    }
    //endregion

    private static List<ImportedFederation> federations(JsonNode root) {
        List<ImportedFederation> result = new ArrayList<>();
        for (JsonNode node : root.path("veranstalter")) {
            result.add(new ImportedFederation(text(node, "veranstalter_id"), text(node, "veranstalterbezeichnung")));
        }
        return result;
    }

    private static List<ImportedClub> clubs(JsonNode root) {
        List<ImportedClub> result = new ArrayList<>();
        for (JsonNode node : root.path("vereine")) {
            result.add(new ImportedClub(text(node, "verein_id"), text(node, "vereinsname"), text(node, "kurzname"),
                text(node, "vereinssitz"), text(node, "veranstalter_id"), !flag(node, "ausgetreten")));
        }
        return result;
    }

    private static List<ImportedPlayer> players(JsonNode root) {
        List<ImportedPlayer> result = new ArrayList<>();
        for (JsonNode node : root.path("spieler")) {
            String genderRaw = text(node, "geschlecht");
            JsonNode birthYear = node.path("geburtsjahr");
            result.add(new ImportedPlayer(text(node, "spieler_id"), text(node, "spielernr"), text(node, "vorname"),
                text(node, "nachname"), birthYear.canConvertToInt() && birthYear.asInt() > 0 ? birthYear.asInt() : null,
                gender(genderRaw), genderRaw, text(node, "lizenz"), text(node, "lizenznr")));
        }
        return result;
    }

    private static List<ImportedMembership> memberships(JsonNode root) {
        List<ImportedMembership> result = new ArrayList<>();
        for (JsonNode node : root.path("mitgliedschaften")) {
            // mitgliedsstatus 0 = left; 1 active, 2 restricted, 3 passive all count as members (SPO-95).
            boolean left = flag(node, "ausgetreten") || node.path("mitgliedsstatus").asInt(1) == 0;
            result.add(new ImportedMembership(text(node, "spieler_id"), text(node, "verein_id"), left,
                instant(text(node, "eintritt")), instant(text(node, "austritt"))));
        }
        return result;
    }

    /** SM stores {@code M} for men and anything else for women; divers isn't recorded there. */
    static PlayerGender gender(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "M" -> PlayerGender.MALE;
            case "W", "F" -> PlayerGender.FEMALE;
            default -> null;
        };
    }

    /** Text value, trimmed; numbers become their text; blank or missing → null. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean flag(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && (value.asBoolean(false) || value.asInt(0) != 0);
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.canConvertToInt() ? null : value.asInt();
    }

    private static Double decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : value.asDouble();
    }

    /** An ISO date; the SM's zero date or anything unreadable → null. */
    private static LocalDate date(String value) {
        if (value == null || value.startsWith("0000")) return null;
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** The SM's {@code datetime} ("2019-03-10 14:00:00"), local German time. */
    private static Instant localDateTime(String value) {
        if (value == null || value.startsWith("0000")) return null;
        try {
            return LocalDateTime.parse(value.replace(' ', 'T')).atZone(SM_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** An ISO instant or an ISO date (start of day UTC); unreadable or missing → null. */
    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return value.length() <= 10 ? LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC)
                : Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
