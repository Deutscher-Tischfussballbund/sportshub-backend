package de.dtfb.sportshub.backend.exception;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import de.dtfb.sportshub.backend.player.PlayerService;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest extends AuthorizedControllerTest {

    @MockitoBean
    PlayerService playerService;

    @Test
    void unexpectedError_expectInternalServerError() throws Exception {
        String id = NanoIdUtils.randomNanoId();

        Mockito.when(playerService.get(any())).thenThrow(new RuntimeException());

        mockMvc.perform(get("/v1/players/" + id))
            .andExpect(status().isInternalServerError());
    }
}
