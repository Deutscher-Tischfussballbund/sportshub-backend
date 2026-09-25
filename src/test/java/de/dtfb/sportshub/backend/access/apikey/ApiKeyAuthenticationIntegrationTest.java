package de.dtfb.sportshub.backend.access.apikey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requests authenticated by an {@code X-API-Key} header, through the real filter chain (no mocked
 * JWT -- key + Authorization together is rejected by design). API keys may only read domain data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyAuthenticationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApiKeyService apiKeyService;

    @Autowired
    ApiKeyRepository apiKeyRepository;

    private String newKey(String name) {
        ApiKeyDto dto = new ApiKeyDto();
        dto.setName(name);
        return apiKeyService.create(dto, "apikey-test-admin").getKey();
    }

    @Test
    void validKey_readsDomainData_andSeesOnlyPublicGender() throws Exception {
        String key = newKey("reader");

        mockMvc.perform(get("/v1/seasons").header("X-API-Key", key))
            .andExpect(status().isOk());
        // player-p9 is DIVERSE_WOMEN in the seed: a key is no admin, so only the public value shows.
        mockMvc.perform(get("/v1/players/player-p9").header("X-API-Key", key))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gender").value("diverse"))
            .andExpect(jsonPath("$.genderDetail").doesNotExist());
    }

    @Test
    void validKey_touchesLastUsedAt() throws Exception {
        String key = newKey("toucher");
        mockMvc.perform(get("/v1/seasons").header("X-API-Key", key)).andExpect(status().isOk());

        assertThat(apiKeyRepository.findByKeyHash(ApiKeyService.hash(key)).orElseThrow().getLastUsedAt()).isNotNull();
    }

    @Test
    void writes_areForbidden_evenOnEndpointsAUserCouldWrite() throws Exception {
        String key = newKey("writer");

        mockMvc.perform(post("/v1/seasons").header("X-API-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"S\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("API_KEY_READ_ONLY"));
        mockMvc.perform(put("/v1/matches/some-id").header("X-API-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"state\": \"PLANNED\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("API_KEY_READ_ONLY"));
    }

    @Test
    void adminAndUserEndpoints_areForbidden() throws Exception {
        String key = newKey("snoop");

        mockMvc.perform(get("/v1/admin/players").header("X-API-Key", key))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("API_KEY_PATH_NOT_ALLOWED"));
        mockMvc.perform(get("/v1/admin/auth/user-search").header("X-API-Key", key))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/auth/me").header("X-API-Key", key))
            .andExpect(status().isForbidden());
    }

    @Test
    void unknownDeactivatedOrExpiredKey_isUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/seasons").header("X-API-Key", "dtfb_not-a-real-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));

        String deactivated = newKey("deactivated");
        ApiKey deactivatedKey = apiKeyRepository.findByKeyHash(ApiKeyService.hash(deactivated)).orElseThrow();
        deactivatedKey.setActive(false);
        apiKeyRepository.save(deactivatedKey);
        mockMvc.perform(get("/v1/seasons").header("X-API-Key", deactivated))
            .andExpect(status().isUnauthorized());

        String expired = newKey("expired");
        ApiKey expiredKey = apiKeyRepository.findByKeyHash(ApiKeyService.hash(expired)).orElseThrow();
        expiredKey.setExpiresAt(LocalDate.now().minusDays(1));
        apiKeyRepository.save(expiredKey);
        mockMvc.perform(get("/v1/seasons").header("X-API-Key", expired))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void keyExpiringToday_isStillValid() throws Exception {
        String key = newKey("last-day");
        ApiKey apiKey = apiKeyRepository.findByKeyHash(ApiKeyService.hash(key)).orElseThrow();
        apiKey.setExpiresAt(LocalDate.now());
        apiKeyRepository.save(apiKey);

        mockMvc.perform(get("/v1/seasons").header("X-API-Key", key)).andExpect(status().isOk());
    }

    @Test
    void keyTogetherWithAuthorizationHeader_isBadRequest() throws Exception {
        mockMvc.perform(get("/v1/seasons").header("X-API-Key", newKey("both"))
                .header("Authorization", "Bearer some.jwt.token"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void noCredentials_isStillUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/seasons")).andExpect(status().isUnauthorized());
    }
}
