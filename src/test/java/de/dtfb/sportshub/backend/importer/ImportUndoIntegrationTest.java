package de.dtfb.sportshub.backend.importer;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerNumberService;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.support.TestIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Undo of an applied run (docs/28): everything it created is gone, everything it changed is back -- and it
 * is refused, with the reasons, as soon as something outside the run depends on it.
 */
class ImportUndoIntegrationTest extends AuthorizedControllerTest {

    private static final AtomicInteger NUMBERS = new AtomicInteger(9000);

    @Autowired private PlayerRepository playerRepository;
    @Autowired private PlayerNumberService numberService;
    @Autowired private ExternalReferenceRepository referenceRepository;
    @Autowired private SeasonRepository seasonRepository;

    @Test
    void undo_removesWhatTheRunCreated() throws Exception {
        Export export = new Export();
        String runId = applied(export.json());

        mockMvc.perform(get("/v1/admin/imports/" + runId + "/undo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.possible").value(true));
        mockMvc.perform(post("/v1/admin/imports/" + runId + "/undo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UNDONE"));

        assertThat(numberService.findPlayer(export.number)).isEmpty();
        assertThat(referenceRepository.findBySourceAndInstance("sportsmanager", export.instance)).isEmpty();
    }

    @Test
    void undo_revertsWhatTheRunChanged_newestFirst() throws Exception {
        Export export = new Export();
        String first = applied(export.json());
        export.lastName = "Geändert";
        String second = applied(export.json());

        undoBlockedBy(first, "LATER_RUN");

        mockMvc.perform(post("/v1/admin/imports/" + second + "/undo")).andExpect(status().isOk());
        assertThat(player(export).getLastName()).isEqualTo("Fiktiv");
        mockMvc.perform(post("/v1/admin/imports/" + first + "/undo")).andExpect(status().isOk());
        assertThat(numberService.findPlayer(export.number)).isEmpty();
    }

    @Test
    void fieldChangedAgainSinceTheRun_blocksTheUndo() throws Exception {
        Export export = new Export();
        applied(export.json());
        export.lastName = "Quelle";
        String second = applied(export.json());
        Player player = player(export);

        mockMvc.perform(put("/v1/admin/players/" + player.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName": "Maren", "lastName": "Hand", "birthYear": 1994, "genderDetail": "female", "active": true}
                    """))
            .andExpect(status().isOk());

        undoBlockedBy(second, "CHANGED_SINCE");
    }

    @Test
    void createdPlayerInUseElsewhere_blocksTheUndo() throws Exception {
        Export export = new Export();
        String runId = applied(export.json());
        joinClub(player(export).getId(), createClub());

        undoBlockedBy(runId, "IN_USE");
        mockMvc.perform(post("/v1/admin/imports/" + runId + "/undo"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IMPORT_UNDO_BLOCKED"));
    }

    @Test
    void historicalSeason_isRemovedAgain() throws Exception {
        String instance = TestIds.unique("undo-hist").toLowerCase();
        String runId = applied(historical(instance));
        String seasonId = referenceRepository.findBySourceAndInstance("sportsmanager", instance).stream()
            .filter(r -> r.getEntityType() == ImportRecordType.SEASON).findFirst().orElseThrow().getEntityId();

        mockMvc.perform(post("/v1/admin/imports/" + runId + "/undo")).andExpect(status().isOk());
        assertThat(seasonRepository.findById(seasonId)).isEmpty();
    }

    @Test
    void reRunThatReplacedGames_cantBeUndone() throws Exception {
        String instance = TestIds.unique("undo-rerun").toLowerCase();
        String league = TestIds.unique("R");
        int numbers = NUMBERS.addAndGet(4) - 3;
        applied(historical(instance, league, numbers, false));
        String corrected = applied(historical(instance, league, numbers, true));

        undoBlockedBy(corrected, "REPLACED_DATA");
    }

    //region helpers
    private static final class Export {
        final String instance = TestIds.unique("undo").toLowerCase();
        final String number = "05-" + NUMBERS.incrementAndGet();
        final String clubName = TestIds.unique("Verein");
        String lastName = "Fiktiv";

        String json() {
            return """
                {
                  "format": "sportshub-sm-export", "version": 1, "instance": "%s", "anonymized": true,
                  "veranstalter": [ { "veranstalter_id": 1, "veranstalterbezeichnung": "Hamburg" } ],
                  "vereine": [ { "verein_id": 10, "vereinsname": "%s", "veranstalter_id": 1 } ],
                  "spieler": [ { "spieler_id": 100, "spielernr": "%s", "vorname": "Maren", "nachname": "%s",
                                 "geschlecht": "W", "geburtsjahr": 1994 } ],
                  "mitgliedschaften": [ { "spieler_id": 100, "verein_id": 10, "mitgliedsstatus": 1 } ]
                }
                """.formatted(instance, clubName, number, lastName);
        }
    }

    private String historical(String instance) throws IOException {
        return historical(instance, TestIds.unique("U"), NUMBERS.addAndGet(4) - 3, false);
    }

    /** The v2 fixture; {@code corrected}: the first game's score changed, as after a correction in the source. */
    private String historical(String instance, String league, int firstNumber, boolean corrected) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/import/sm-export-v2.json")) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                .replace("{{instance}}", instance).replace("{{league}}", league);
            for (int i = 1; i <= 4; i++) json = json.replace("{{n" + i + "}}", "05-" + (firstNumber + i - 1));
            return corrected ? json.replaceFirst("\"teamspiel_gast_punkte\": 4", "\"teamspiel_gast_punkte\": 5") : json;
        }
    }

    private Player player(Export export) {
        return playerRepository.findById(numberService.findPlayer(export.number).orElseThrow().getId()).orElseThrow();
    }

    private String applied(String json) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/v1/admin/imports")
                .file(new MockMultipartFile("file", "export.json", MediaType.APPLICATION_JSON_VALUE,
                    json.getBytes(StandardCharsets.UTF_8)))
                .param("source", "sportsmanager")
                .param("targetFederationId", "fed-hh"))
            .andExpect(status().isOk()).andReturn();
        String runId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/v1/admin/imports/" + runId + "/apply")).andExpect(status().isOk());
        return runId;
    }

    private void undoBlockedBy(String runId, String code) throws Exception {
        mockMvc.perform(get("/v1/admin/imports/" + runId + "/undo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.possible").value(false))
            .andExpect(jsonPath("$.blockers[*].code", hasItem(code)));
    }
    //endregion
}
