package de.dtfb.sportshub.backend.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.dtfb.sportshub.backend.importer.sportsmanager.SportsManagerImportSource;
import de.dtfb.sportshub.backend.player.PlayerGender;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The SM adapter only normalizes -- trimming, blanks, gender codes, flags, dates (docs/28). */
class SportsManagerImportSourceTest {

    private final SportsManagerImportSource source =
        new SportsManagerImportSource(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void parsesTheFixture() throws Exception {
        ImportBatch batch;
        try (InputStream in = getClass().getResourceAsStream("/import/sm-export-v1.json")) {
            batch = source.parse(in, "sm-export-v1.json");
        }

        assertThat(batch.header().instance()).isEqualTo("tfvhh");
        assertThat(batch.header().anonymized()).isTrue();
        assertThat(batch.header().exportedAt()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));

        assertThat(batch.federations()).containsExactly(new ImportedFederation("1", "Hamburg"));
        assertThat(batch.clubs().getFirst())
            .isEqualTo(new ImportedClub("10", "Kickerfreunde Alster", "KFA", "Hamburg", "1", true));
        assertThat(batch.clubs().get(1).shortName()).isNull();
        assertThat(batch.clubs().get(1).active()).isFalse();

        ImportedPlayer maren = batch.players().getFirst();
        assertThat(maren.gender()).isEqualTo(PlayerGender.FEMALE);
        assertThat(maren.nationalLicense()).isEqualTo("B");
        assertThat(maren.internationalId()).isEqualTo("ITSF-1");
        ImportedPlayer olaf = batch.players().get(1);
        assertThat(olaf.number()).isNull();
        assertThat(olaf.birthYear()).as("0 = unknown in the SM").isNull();
        assertThat(batch.players().get(2).firstName()).isNull();
        ImportedPlayer kim = batch.players().get(3);
        assertThat(kim.gender()).isNull();
        assertThat(kim.genderRaw()).isEqualTo("X");

        assertThat(batch.memberships().getFirst().joinedAt()).isEqualTo(Instant.parse("2015-03-01T00:00:00Z"));
        assertThat(batch.memberships().getFirst().left()).isFalse();
        assertThat(batch.memberships().get(1).left()).isTrue();
        assertThat(batch.memberships().get(1).leftAt()).as("SM's zero date").isNull();
    }

    @Test
    void refusesOtherFormats() {
        assertThatThrownBy(() -> source.parse(stream("{\"format\": \"something\"}"), "x.json"))
            .isInstanceOf(ImportFormatException.class);
        assertThatThrownBy(() -> source.parse(stream("not json"), "x.json"))
            .isInstanceOf(ImportFormatException.class);
        assertThatThrownBy(() -> source.parse(
            stream("{\"format\": \"sportshub-sm-export\", \"version\": 3, \"instance\": \"x\"}"), "x.json"))
            .isInstanceOf(ImportFormatException.class);
    }

    @Test
    void parsesPastSeasons_v2() throws Exception {
        ImportBatch batch;
        try (InputStream in = getClass().getResourceAsStream("/import/sm-export-v2.json")) {
            batch = source.parse(in, "sm-export-v2.json");
        }

        assertThat(batch.seasons()).containsExactly(new ImportedSeason("5", "2019",
            java.time.LocalDate.of(2019, 3, 1), java.time.LocalDate.of(2019, 9, 30)));
        ImportedLeague league = batch.leagues().getFirst();
        assertThat(league.tableRule()).isEqualTo(1);
        assertThat(league.mode().gamePlan()).containsExactly(
            de.dtfb.sportshub.backend.match.MatchType.DOUBLE, de.dtfb.sportshub.backend.match.MatchType.DOUBLE,
            de.dtfb.sportshub.backend.match.MatchType.SINGLE, de.dtfb.sportshub.backend.match.MatchType.SINGLE);
        assertThat(league.mode().raceTarget()).isNull();

        ImportedTeam elbe = batch.teams().get(1);
        assertThat(elbe.identityExternalId()).as("no teamgruppe -> its own id").isEqualTo("501");
        assertThat(elbe.table().points()).isEqualTo(-1.0);
        assertThat(elbe.table().adjustment()).isEqualTo(-1.0);

        ImportedFixture played = batch.fixtures().getFirst();
        assertThat(played.kickOff()).as("SM times are German local time")
            .isEqualTo(Instant.parse("2019-03-10T13:00:00Z"));
        assertThat(played.played()).isTrue();
        assertThat(played.games()).hasSize(4);
        assertThat(played.games().get(2).homePlayer2()).as("0 = no player").isNull();
        assertThat(played.games().getFirst().sets()).hasSize(1);
        assertThat(played.games().get(1).sets()).as("no set details in old data").isEmpty();
        assertThat(batch.fixtures().get(1).played()).isFalse();
        assertThat(batch.fixtures().get(1).matchdayTitle()).isEqualTo("Rückrunde");
        assertThat(batch.rosterEntries().get(1).left()).isTrue();
    }

    @Test
    void aPlayerListedTwiceInOneTeam_isOneRosterEntry_activeIfAnyRowIs() {
        ImportBatch batch = source.parse(stream("""
            {"format": "sportshub-sm-export", "version": 2, "instance": "x", "kader": [
              {"spieler_id": "7", "team_id": 90, "ausgetreten": 0},
              {"spieler_id": "7", "team_id": 90, "ausgetreten": 1},
              {"spieler_id": "8", "team_id": 90, "ausgetreten": 1},
              {"spieler_id": "8", "team_id": 90, "ausgetreten": 1}
            ]}
            """), "x.json");

        assertThat(batch.rosterEntries()).containsExactly(
            new ImportedRosterEntry("7", "90", false), new ImportedRosterEntry("8", "90", true));
    }

    @Test
    void tableRules_andLeagueNames() {
        assertThat(HistoricalPlanner.points(1)).containsExactly(2, 1, 0);
        assertThat(HistoricalPlanner.points(4)).containsExactly(3, 1, 0);
        assertThat(HistoricalPlanner.points(7)).containsExactly(1, 0, 0);
        assertThat(HistoricalPlanner.points(24)).containsExactly(3, 1, 0);
        assertThat(HistoricalPlanner.points(-2)).containsExactly(2, 1, 0);
        assertThat(HistoricalPlanner.identityName("Landesliga Nord 2019/20")).isEqualTo("landesliga nord");
        assertThat(HistoricalPlanner.identityName("2. Bundesliga – Saison 2021")).isEqualTo("2 bundesliga saison");
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
