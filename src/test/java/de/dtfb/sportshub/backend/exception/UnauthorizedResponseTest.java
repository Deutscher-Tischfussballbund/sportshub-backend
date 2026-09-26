package de.dtfb.sportshub.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request without credentials gets a 401 with the usual ApiError body (SPO-66); Spring's
 * WWW-Authenticate header is kept.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UnauthorizedResponseTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void readWithoutCredentials_is401WithApiErrorBody() throws Exception {
        mockMvc.perform(get("/v1/seasons"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists("WWW-Authenticate"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void writeWithoutCredentials_is401WithApiErrorBody() throws Exception {
        mockMvc.perform(delete("/v1/seasons/some-id"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
