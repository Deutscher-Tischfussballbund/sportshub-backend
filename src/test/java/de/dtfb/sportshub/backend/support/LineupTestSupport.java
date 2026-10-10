package de.dtfb.sportshub.backend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Line-ups are required by default (docs/23). Tests about result entry itself switch them off in the
 * league's own rules, so they don't have to line up players first.
 */
public final class LineupTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private LineupTestSupport() {
    }

    /** Sets {@code lineupRequired: false} on the league's own rule set; {@code as} may be null (default auth). */
    public static void disableLineups(MockMvc mockMvc, RequestPostProcessor as, String leagueId) throws Exception {
        String league = mockMvc.perform(with(get("/v1/leagues/" + leagueId), as))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String ruleSetId = JsonPath.read(league, "$.ruleSetId");
        String rules = mockMvc.perform(with(get("/v1/league-rule-sets/" + ruleSetId), as))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ObjectNode node = (ObjectNode) JSON.readTree(rules);
        node.put("lineupRequired", false);
        mockMvc.perform(with(put("/v1/league-rule-sets/" + ruleSetId), as)
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(node)))
            .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder request, RequestPostProcessor as) {
        return as == null ? request : request.with(as);
    }
}
