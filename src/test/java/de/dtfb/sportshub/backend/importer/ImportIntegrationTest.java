package de.dtfb.sportshub.backend.importer;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerNumberKind;
import de.dtfb.sportshub.backend.player.PlayerNumberService;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.support.TestIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The import pipeline end to end with the Sports Manager source (docs/28): preview, apply, re-run,
 * conflicts, stale previews, issued numbers, manual matches. Every test uses its own SM installation
 * name and player numbers -- the test classes share one database.
 */
class ImportIntegrationTest extends AuthorizedControllerTest {

    private static final AtomicInteger NUMBERS = new AtomicInteger(5000);

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerNumberService numberService;

    @Autowired
    private ImportRunGate gate;

    @Test
    void preview_classifiesEveryRecord_withoutWritingAnything() throws Exception {
        Export export = new Export();
        long playersBefore = playerRepository.count();

        String runId = upload(export.json()).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PREVIEWED"))
            .andExpect(jsonPath("$.instance").value(export.instance))
            .andReturn().getResponse().getContentAsString().transform(json -> JsonPath.read(json, "$.id"));

        assertThat(playerRepository.count()).isEqualTo(playersBefore);
        Map<String, Map<String, Object>> items = items(runId);

        assertThat(items.get("FEDERATION:1")).containsEntry("action", "NEW").containsEntry("targetEntityId", "fed-hh");
        assertThat(codes(items.get("CLUB:11"))).containsExactly("FEDERATION_FALLBACK");
        assertThat(items.get("PLAYER:100")).containsEntry("action", "NEW");
        assertThat(codes(items.get("PLAYER:101")))
            .containsExactlyInAnyOrder("MISSING_BIRTH_YEAR", "NUMBER_WILL_BE_ISSUED");
        assertThat(items.get("PLAYER:102")).containsEntry("action", "REJECTED");
        assertThat(codes(items.get("PLAYER:102"))).containsExactly("MISSING_NAME");
        assertThat(codes(items.get("PLAYER:103"))).containsExactly("UNKNOWN_GENDER");
        assertThat(codes(items.get("CLUB_MEMBERSHIP:102:10"))).containsExactly("BLOCKED_BY_REJECTED_RECORD");
    }

    @Test
    void apply_writes_thenTheSameFileAgainChangesNothing() throws Exception {
        Export export = new Export();
        String runId = previewId(export.json());
        mockMvc.perform(post("/v1/admin/imports/" + runId + "/apply"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPLIED"));

        Player maren = playerByNumber(export.maren);
        assertThat(maren.getFirstName()).isEqualTo("Maren");
        Player olaf = playerByNameAndNumberPrefix(export.olafLastName, "NG-");
        assertThat(olaf.isComplete()).as("no birth year in the SM").isFalse();
        mockMvc.perform(get("/v1/admin/players").param("q", export.maren))
            .andExpect(jsonPath("$[0].clubs[0].name").value("Kickerfreunde Alster"))
            .andExpect(jsonPath("$[0].complete").value(true));

        String again = previewId(export.json());
        Map<String, Map<String, Object>> items = items(again);
        assertThat(items.values()).allSatisfy(item ->
            assertThat(item.get("action")).isIn("UNCHANGED", "REJECTED"));
        assertThat(items.get("PLAYER:101")).containsEntry("targetEntityId", olaf.getId());
    }

    @Test
    void sourceChange_updates_butALocalEditOfTheSameFieldConflicts() throws Exception {
        Export export = new Export();
        apply(previewId(export.json()));
        Player maren = playerByNumber(export.maren);

        mockMvc.perform(put("/v1/admin/players/" + maren.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName": "Maren", "lastName": "Lokal", "birthYear": 1994, "genderDetail": "female", "active": true}
                    """))
            .andExpect(status().isOk());

        export.marenLastName = "Quelle";
        Map<String, Map<String, Object>> items = items(previewId(export.json()));
        assertThat(items.get("PLAYER:100")).containsEntry("action", "CONFLICT");
        assertThat(codes(items.get("PLAYER:100"))).containsExactly("CHANGED_LOCALLY");

        export.marenLastName = "Lokal";
        export.marenFirstName = "Marena";
        items = items(previewId(export.json()));
        assertThat(items.get("PLAYER:100")).containsEntry("action", "UPDATE");
    }

    @Test
    void realNumberFromTheSource_replacesTheIssuedOne_whichStaysAnAlias() throws Exception {
        Export export = new Export();
        apply(previewId(export.json()));
        Player olaf = playerByNameAndNumberPrefix(export.olafLastName, "NG-");
        String issued = olaf.getNationalId();

        export.olafNumber = "05-" + NUMBERS.incrementAndGet();
        apply(previewId(export.json()));

        assertThat(playerRepository.findById(olaf.getId()).orElseThrow().getNationalId()).isEqualTo(export.olafNumber);
        assertThat(numberService.findPlayer(issued)).map(Player::getId).contains(olaf.getId());
    }

    @Test
    void hobbyPlayer_assignedByHand_getsTheRealNumber() throws Exception {
        Player hobby = new Player();
        hobby.setFirstName("Maren");
        hobby.setLastName(TestIds.unique("Hobby"));
        hobby.setBirthYear(1994);
        playerRepository.save(hobby);
        String hobbyNumber = numberService.issue(hobby, PlayerNumberKind.HOBBY);
        assertThat(hobbyNumber).startsWith("XX-");

        Export export = new Export();
        export.marenLastName = hobby.getLastName();
        String runId = previewId(export.json());
        Map<String, Object> maren = items(runId).get("PLAYER:100");
        assertThat(codes(maren)).containsExactly("DUPLICATE_SUSPECT");

        mockMvc.perform(put("/v1/admin/imports/" + runId + "/items/" + maren.get("id") + "/match")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\": \"" + hobby.getId() + "\"}"))
            .andExpect(status().isOk());
        Map<String, Object> matched = items(runId).get("PLAYER:100");
        assertThat(matched).containsEntry("action", "UPDATE").containsEntry("manualMatchId", hobby.getId());
        apply(runId);

        assertThat(playerRepository.findById(hobby.getId()).orElseThrow().getNationalId()).isEqualTo(export.maren);
        assertThat(numberService.findPlayer(hobbyNumber)).map(Player::getId).contains(hobby.getId());
    }

    @Test
    void apply_refusesAStalePreview() throws Exception {
        Export export = new Export();
        String first = previewId(export.json());
        apply(previewId(export.json()));

        mockMvc.perform(post("/v1/admin/imports/" + first + "/apply"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IMPORT_STALE"));
    }

    @Test
    void applyWhileApplying_isBusy_andTheStatusShowsIt() throws Exception {
        String runId = previewId(new Export().json());
        assertThat(gate.claim(runId, ImportRunStatus.PREVIEWED, ImportRunStatus.APPLYING)).isTrue();

        mockMvc.perform(get("/v1/admin/imports/" + runId)).andExpect(jsonPath("$.status").value("APPLYING"));
        mockMvc.perform(post("/v1/admin/imports/" + runId + "/apply"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IMPORT_RUN_BUSY"));

        gate.release(runId, ImportRunStatus.APPLYING, ImportRunStatus.PREVIEWED);
        apply(runId);
    }

    @Test
    void discard_closesTheRun() throws Exception {
        String runId = previewId(new Export().json());
        mockMvc.perform(delete("/v1/admin/imports/" + runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISCARDED"));
        mockMvc.perform(post("/v1/admin/imports/" + runId + "/apply"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IMPORT_RUN_CLOSED"));
    }

    @Test
    void notAnSmExport_isA400() throws Exception {
        upload("{\"hello\": \"world\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("IMPORT_FORMAT"))
            .andExpect(jsonPath("$.message", containsString("Sports Manager")));
    }

    @Test
    void sources_listTheSportsManager() throws Exception {
        mockMvc.perform(get("/v1/admin/imports/sources"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("sportsmanager"));
    }

    //region helpers
    /** A small SM export with its own installation name and numbers; fields tests change are mutable. */
    private static final class Export {
        final String instance = TestIds.unique("sm").toLowerCase();
        final String maren = "05-" + NUMBERS.incrementAndGet();
        String marenFirstName = "Maren";
        String marenLastName = TestIds.unique("Fiktiv");
        final String olafLastName = TestIds.unique("Erfunden");
        String olafNumber;

        String json() {
            return """
                {
                  "format": "sportshub-sm-export", "version": 1, "instance": "%s",
                  "exportedAt": "2026-10-10T12:00:00Z", "anonymized": true,
                  "veranstalter": [ { "veranstalter_id": 1, "veranstalterbezeichnung": "Hamburg" } ],
                  "vereine": [
                    { "verein_id": 10, "vereinsname": "Kickerfreunde Alster", "vereinssitz": "Hamburg", "veranstalter_id": 1 },
                    { "verein_id": 11, "vereinsname": "TFC Elbe", "veranstalter_id": 99 }
                  ],
                  "spieler": [
                    { "spieler_id": 100, "spielernr": "%s", "vorname": "%s", "nachname": "%s", "geschlecht": "W", "geburtsjahr": 1994 },
                    { "spieler_id": 101, "spielernr": %s, "vorname": "Olaf", "nachname": "%s", "geschlecht": "M" },
                    { "spieler_id": 102, "vorname": "", "nachname": "Ohnename", "geschlecht": "M", "geburtsjahr": 1990 },
                    { "spieler_id": 103, "vorname": "Kim", "nachname": "Rätsel", "geschlecht": "X", "geburtsjahr": 1999 }
                  ],
                  "mitgliedschaften": [
                    { "spieler_id": 100, "verein_id": 10, "mitgliedsstatus": 1 },
                    { "spieler_id": 101, "verein_id": 11, "mitgliedsstatus": 0 },
                    { "spieler_id": 102, "verein_id": 10, "mitgliedsstatus": 1 }
                  ]
                }
                """.formatted(instance, maren, marenFirstName, marenLastName,
                olafNumber == null ? "null" : "\"" + olafNumber + "\"", olafLastName);
        }
    }

    private ResultActions upload(String json) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "export.json", MediaType.APPLICATION_JSON_VALUE,
            json.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/v1/admin/imports").file(file)
            .param("source", "sportsmanager")
            .param("targetFederationId", "fed-hh"));
    }

    private String previewId(String json) throws Exception {
        MvcResult result = upload(json).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void apply(String runId) throws Exception {
        mockMvc.perform(post("/v1/admin/imports/" + runId + "/apply")).andExpect(status().isOk());
    }

    /** All items of a run, keyed "TYPE:externalId". */
    private Map<String, Map<String, Object>> items(String runId) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/admin/imports/" + runId + "/items").param("size", "500"))
            .andExpect(status().isOk())
            .andReturn();
        List<Map<String, Object>> items = JsonPath.read(result.getResponse().getContentAsString(), "$.items");
        return items.stream().collect(java.util.stream.Collectors.toMap(
            item -> item.get("recordType") + ":" + item.get("externalId"), item -> item));
    }

    @SuppressWarnings("unchecked")
    private static List<String> codes(Map<String, Object> item) {
        return ((List<Map<String, Object>>) item.get("issues")).stream()
            .map(issue -> (String) issue.get("code")).toList();
    }

    private Player playerByNumber(String number) {
        return numberService.findPlayer(number).orElseThrow();
    }

    private Player playerByNameAndNumberPrefix(String lastName, String prefix) {
        return playerRepository.findAll().stream()
            .filter(p -> lastName.equals(p.getLastName()) && p.getNationalId() != null
                && p.getNationalId().startsWith(prefix))
            .reduce((first, second) -> second)
            .orElseThrow();
    }
    //endregion
}
