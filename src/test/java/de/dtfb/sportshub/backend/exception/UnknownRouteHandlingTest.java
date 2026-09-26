package de.dtfb.sportshub.backend.exception;

import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request Spring can't route is a client error, not a server crash (SPO-66): the catch-all
 * {@code Exception} handler used to turn these into 500 INTERNAL_ERROR.
 */
class UnknownRouteHandlingTest extends AuthorizedControllerTest {

    @Test
    void unknownPath_isNotFound() throws Exception {
        mockMvc.perform(get("/v1/does-not-exist"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodOnKnownPath_isMethodNotAllowed() throws Exception {
        // /v1/seasons exists (GET, POST) but has no collection-level DELETE.
        mockMvc.perform(delete("/v1/seasons"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
