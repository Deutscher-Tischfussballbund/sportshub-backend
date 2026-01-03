package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.externalApi.ExternalApiClient;
import de.dtfb.sportshub.backend.externalApi.ExternalApiException;
import de.dtfb.sportshub.backend.externalApi.ExternalApiUnavailableException;
import de.dtfb.sportshub.backend.externalApi.ExternalResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
class PlayerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ExternalApiClient externalApiClient;

    @Test
    void getPlayer() throws Exception {
        UUID id = UUID.randomUUID();
        PlayerDto mockDto = new PlayerDto();
        mockDto.setId(id);
        mockDto.setFirstName("Test");
        mockDto.setLastName("Test");

        Mockito.when(externalApiClient.fetchById(any())).thenReturn(mockDto);

        mockMvc.perform(get("/api/v1/players/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value(mockDto.getFirstName()))
            .andExpect(jsonPath("$.lastName").value(mockDto.getLastName()))
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getPlayer_serviceDown_expectException() throws Exception {
        UUID id = UUID.randomUUID();

        Mockito.when(externalApiClient.fetchById(any())).thenThrow(new ExternalApiUnavailableException());

        mockMvc.perform(get("/api/v1/players/" + id))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getUnknownPlayer_expectException() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.when(externalApiClient.fetchById(any())).thenThrow(new ExternalResourceNotFoundException());
        mockMvc.perform(get("/api/v1/players/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    void getPlayer_badRequest_expectException() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.when(externalApiClient.fetchById(any())).thenThrow(new ExternalApiException("Bad Request"));
        mockMvc.perform(get("/api/v1/players/" + id))
            .andExpect(status().isBadRequest());
    }
}
