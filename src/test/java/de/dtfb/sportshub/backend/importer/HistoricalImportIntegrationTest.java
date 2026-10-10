package de.dtfb.sportshub.backend.importer;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.lineup.LineupRepository;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.matchday.SchedulingState;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.roster.RosterEntry;
import de.dtfb.sportshub.backend.roster.RosterEntryRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.support.TestIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Past seasons through the importer (docs/29): a finished SM league becomes final history with its frozen
 * official table; running seasons and cups stay out; leagues link across seasons by name. Every test uses
 * its own SM installation, league name and player numbers -- the test classes share one database.
 */
class HistoricalImportIntegrationTest extends AuthorizedControllerTest {

    private static final AtomicInteger NUMBERS = new AtomicInteger(7000);

    @Autowired private ExternalReferenceRepository referenceRepository;
    @Autowired private LeagueRepository leagueRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private MatchDayRepository matchDayRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private LineupRepository lineupRepository;
    @Autowired private RosterEntryRepository rosterRepository;

    @Test
    void preview_takesTheTeamLeague_skipsTheCup() throws Exception {
        Export export = new Export();
        Map<String, Map<String, Object>> items = items(previewId(export.json()));

        assertThat(items.get("SEASON:5")).containsEntry("action", "NEW");
        assertThat(items.get("LEAGUE:50")).containsEntry("action", "NEW");
        assertThat((String) items.get("LEAGUE:50").get("linkId")).startsWith("batch:league:");
        assertThat(codes(items.get("LEAGUE:51"))).containsExactly("CUP_OR_KNOCKOUT");
        assertThat(items.get("TEAM:500")).containsEntry("action", "NEW");
        assertThat(items.get("ROSTER_ENTRY:" + export.playerId(3) + ":501")).containsEntry("action", "NEW");
        assertThat(items.get("FIXTURE:900")).containsEntry("action", "NEW");
        assertThat(codes(items.get("FIXTURE:901"))).containsExactly("NOT_PLAYED");
    }

    @Test
    @Transactional
    void apply_writesFinalHistory_withTheFrozenTable() throws Exception {
        Export export = new Export();
        apply(previewId(export.json()));

        League league = leagueRepository.findById(entityId(export, ImportRecordType.LEAGUE, "50")).orElseThrow();
        assertThat(league.getRuleSet().isSnapshot()).isTrue();
        String groupId = groupRepository.findByTier_League_Id(league.getId()).getFirst().getId();

        mockMvc.perform(get("/v1/groups/" + groupId + "/standings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].teamName").value("KFA 1"))
            .andExpect(jsonPath("$[0].place").value(1))
            .andExpect(jsonPath("$[0].frozen").value(true))
            .andExpect(jsonPath("$[1].teamName").value("TFC Elbe"))
            .andExpect(jsonPath("$[1].points").value(-1))
            .andExpect(jsonPath("$[1].pointsAdjustment").value(-1));

        MatchDay played = matchDayRepository.findById(entityId(export, ImportRecordType.FIXTURE, "900")).orElseThrow();
        assertThat(played.getResultState()).isEqualTo(ResultState.CONFIRMED);
        assertThat(played.hasBeenFinal()).as("final: admin-only from now on").isTrue();
        assertThat(played.getSchedulingState()).as("the date is history").isEqualTo(SchedulingState.CONFIRMED);
        assertThat(matchRepository.findByMatchDay(played)).hasSize(4);
        assertThat(lineupRepository.findByMatchDay(played)).hasSize(2);
        MatchDay open = matchDayRepository.findById(entityId(export, ImportRecordType.FIXTURE, "901")).orElseThrow();
        assertThat(open.getResultState()).isEqualTo(ResultState.OPEN);
        assertThat(open.getRound().getName()).isEqualTo("Rückrunde");

        RosterEntry left = rosterRepository.findById(
            entityId(export, ImportRecordType.ROSTER_ENTRY, export.playerId(2) + ":500")).orElseThrow();
        assertThat(left.getRemovedAt()).isNotNull();
    }

    @Test
    void sameFileAgain_changesNothing() throws Exception {
        Export export = new Export();
        apply(previewId(export.json()));

        Map<String, Map<String, Object>> items = items(previewId(export.json()));
        assertThat(items.values()).allSatisfy(item -> assertThat(item.get("action")).isIn("UNCHANGED", "REJECTED"));
    }

    @Test
    void runningSeason_isRejected() throws Exception {
        Export export = new Export();
        export.lastDay = "2099-09-30";
        Map<String, Map<String, Object>> items = items(previewId(export.json()));

        assertThat(codes(items.get("SEASON:5"))).containsExactly("SEASON_NOT_ENDED");
        assertThat(codes(items.get("LEAGUE:50"))).containsExactly("BLOCKED_BY_REJECTED_RECORD");
    }

    @Test
    void sameLeagueNextSeason_isLinkedByName_orKeptApartByHand() throws Exception {
        Export first = new Export();
        apply(previewId(first.json()));
        String identity = leagueRepository.findById(entityId(first, ImportRecordType.LEAGUE, "50")).orElseThrow()
            .getLeagueIdentityId();

        Export second = new Export(first.league);
        String runId = previewId(second.json());
        Map<String, Object> league = items(runId).get("LEAGUE:50");
        assertThat(codes(league)).containsExactly("LINKED_BY_NAME");
        assertThat(league).containsEntry("linkId", identity);

        mockMvc.perform(put("/v1/admin/imports/" + runId + "/items/" + league.get("id") + "/match")
                .contentType(MediaType.APPLICATION_JSON).content("{\"playerId\": \"own\"}"))
            .andExpect(status().isOk());
        Map<String, Object> apart = items(runId).get("LEAGUE:50");
        assertThat(apart.get("linkId")).isNull();
        assertThat(codes(apart)).containsExactly("MATCHED_MANUALLY");

        mockMvc.perform(put("/v1/admin/imports/" + runId + "/items/" + apart.get("id") + "/match")
                .contentType(MediaType.APPLICATION_JSON).content("{\"playerId\": null}"))
            .andExpect(status().isOk());
        apply(runId);
        assertThat(leagueRepository.findById(entityId(second, ImportRecordType.LEAGUE, "50")).orElseThrow()
            .getLeagueIdentityId()).isEqualTo(identity);
    }

    @Test
    void sourceTableNotMatchingTheResults_isFlagged() throws Exception {
        Export export = new Export();
        export.winnerWins = 0;
        Map<String, Map<String, Object>> items = items(previewId(export.json()));

        Map<String, Object> league = items.get("LEAGUE:50");
        assertThat(codes(league)).containsExactly("TABLE_DIFFERS");
        assertThat(((List<Map<String, Object>>) league.get("issues")).getFirst().get("detail")).isEqualTo("KFA 1");
    }

    //region helpers
    /** The v2 fixture with its own installation, league name and numbers. */
    private static final class Export {
        final String instance = TestIds.unique("hist").toLowerCase();
        final String league;
        final String[] numbers = new String[4];
        String lastDay = "2019-09-30";
        int winnerWins = 1;

        Export() {
            this(TestIds.unique("L"));
        }

        Export(String league) {
            this.league = league;
            for (int i = 0; i < numbers.length; i++) numbers[i] = "05-" + NUMBERS.incrementAndGet();
        }

        /** Player source ids aren't pseudonymized in the fixture: 100..103. */
        String playerId(int n) {
            return Integer.toString(99 + n);
        }

        String json() throws IOException {
            String template;
            try (InputStream in = getClass().getResourceAsStream("/import/sm-export-v2.json")) {
                template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String json = template.replace("{{instance}}", instance).replace("{{league}}", league)
                .replace("\"letzter_tag\": \"2019-09-30\"", "\"letzter_tag\": \"" + lastDay + "\"")
                .replace("\"siege\": 1,", "\"siege\": " + winnerWins + ",");
            for (int i = 0; i < numbers.length; i++) json = json.replace("{{n" + (i + 1) + "}}", numbers[i]);
            return json;
        }
    }

    private String entityId(Export export, ImportRecordType type, String externalId) {
        return referenceRepository.findBySourceAndInstance("sportsmanager", export.instance).stream()
            .filter(r -> r.getEntityType() == type && r.getExternalId().equals(externalId))
            .findFirst().orElseThrow().getEntityId();
    }

    private ResultActions upload(String json) throws Exception {
        return mockMvc.perform(multipart("/v1/admin/imports")
            .file(new MockMultipartFile("file", "export.json", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)))
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

    private Map<String, Map<String, Object>> items(String runId) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/admin/imports/" + runId + "/items").param("size", "500"))
            .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> items = JsonPath.read(result.getResponse().getContentAsString(), "$.items");
        return items.stream().collect(Collectors.toMap(
            item -> item.get("recordType") + ":" + item.get("externalId"), item -> item));
    }

    @SuppressWarnings("unchecked")
    private static List<String> codes(Map<String, Object> item) {
        return ((List<Map<String, Object>>) item.get("issues")).stream()
            .map(issue -> (String) issue.get("code")).toList();
    }
    //endregion
}
