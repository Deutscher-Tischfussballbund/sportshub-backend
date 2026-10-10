package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A test system ({@code anonymized: required}) refuses real exports (docs/28). */
@TestPropertySource(properties = "sportshub.importer.anonymized=required")
class ImportAnonymizationPolicyTest extends AuthorizedControllerTest {

    @Test
    void realExport_isRefused() throws Exception {
        String json = """
            {"format": "sportshub-sm-export", "version": 1, "instance": "policy", "anonymized": false}
            """;
        mockMvc.perform(multipart("/v1/admin/imports")
                .file(new MockMultipartFile("file", "x.json", MediaType.APPLICATION_JSON_VALUE,
                    json.getBytes(StandardCharsets.UTF_8)))
                .param("source", "sportsmanager")
                .param("targetFederationId", "fed-hh"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("IMPORT_ANONYMIZATION_REQUIRED"));
    }
}
