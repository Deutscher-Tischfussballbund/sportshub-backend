package de.dtfb.sportshub.backend.season;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A season holding results must be refused with 409 + a structured body. {@link SeasonStructure} is
 * mocked to report results so we don't have to build the deep competition/result chain here.
 */
class SeasonDeletionBlockedTest extends AuthorizedControllerTest {

    @MockitoBean
    SeasonStructure structure;

    @Test
    void delete_seasonWithResults_returns409WithContents() throws Exception {
        String federationId = createFederation();
        MvcResult created = mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"S\",\"federationId\":\"" + federationId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        Mockito.when(structure.contentsOf(anyString()))
            .thenReturn(new SeasonContents(2, 10, 3, 5));   // hasResults() == true

        mockMvc.perform(delete("/v1/seasons/" + id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SEASON_HAS_RESULTS"))
            .andExpect(jsonPath("$.attached.leagues").value(2))
            .andExpect(jsonPath("$.attached.matchDaysWithResults").value(3))
            .andExpect(jsonPath("$.attached.standings").value(5));

        Mockito.verify(structure, Mockito.never()).deleteStructure(anyString());
    }
}
