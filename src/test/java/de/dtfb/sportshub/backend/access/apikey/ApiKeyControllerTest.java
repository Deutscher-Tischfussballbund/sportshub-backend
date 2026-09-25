package de.dtfb.sportshub.backend.access.apikey;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Global-admin management of API keys (SPO-48, docs/20): the plaintext key is returned exactly once. */
class ApiKeyControllerTest extends AuthorizedControllerTest {

    @Test
    void create_returnsKeyOnce_andListNeverContainsIt() throws Exception {
        MvcResult created = create("Results Site", null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(startsWith("dtfb_")))
            .andExpect(jsonPath("$.apiKey.name").value("Results Site"))
            .andExpect(jsonPath("$.apiKey.active").value(true))
            .andExpect(jsonPath("$.apiKey.keyHash").doesNotExist())
            .andReturn();
        String body = created.getResponse().getContentAsString();
        String key = JsonPath.read(body, "$.key");
        String id = JsonPath.read(body, "$.apiKey.id");
        String prefix = JsonPath.read(body, "$.apiKey.keyPrefix");
        org.assertj.core.api.Assertions.assertThat(key).startsWith(prefix);

        mockMvc.perform(get("/v1/admin/api-keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + id + "')].keyPrefix").value(prefix))
            .andExpect(jsonPath("$[?(@.id == '" + id + "')].key").doesNotExist())
            .andExpect(jsonPath("$[?(@.id == '" + id + "')].keyHash").doesNotExist());
    }

    @Test
    void create_withoutName_orPastExpiry_isBadRequest() throws Exception {
        create(" ", null).andExpect(status().isBadRequest());
        create("Old", LocalDate.now().minusDays(1)).andExpect(status().isBadRequest());
    }

    @Test
    void update_changesNameActiveAndExpiry_thenDelete() throws Exception {
        String id = JsonPath.read(create("Before", null).andReturn().getResponse().getContentAsString(), "$.apiKey.id");
        String expiry = LocalDate.now().plusMonths(6).toString();

        mockMvc.perform(put("/v1/admin/api-keys/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"After\", \"active\": false, \"expiresAt\": \"" + expiry + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("After"))
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.expiresAt").value(expiry));

        mockMvc.perform(delete("/v1/admin/api-keys/" + id)).andExpect(status().isOk());
        mockMvc.perform(delete("/v1/admin/api-keys/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void nonAdmin_isForbidden() throws Exception {
        mockMvc.perform(get("/v1/admin/api-keys").with(jwt().jwt(j -> j.claim("dtfb_id", "apikey-outsider"))))
            .andExpect(status().isForbidden());
    }

    private ResultActions create(String name, LocalDate expiresAt) throws Exception {
        return mockMvc.perform(post("/v1/admin/api-keys")
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("{\"name\": \"%s\", \"expiresAt\": %s}",
                name, expiresAt == null ? "null" : "\"" + expiresAt + "\"")));
    }
}
