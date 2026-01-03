package de.dtfb.sportshub.backend.exception;

import de.dtfb.sportshub.backend.externalApi.ExternalApiClient;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ExternalApiClient externalApiClient;

    @Test
    void getPlayer_expectException() throws Exception {
        UUID id = UUID.randomUUID();

        Mockito.when(externalApiClient.fetchById(any())).thenThrow(new RuntimeException());

        mockMvc.perform(get("/api/v1/players/" + id))
            .andExpect(status().isInternalServerError());
    }
}
