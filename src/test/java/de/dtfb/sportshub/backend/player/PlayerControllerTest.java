package de.dtfb.sportshub.backend.player;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayerControllerTest extends AuthorizedControllerTest {

    @MockitoBean
    PlayerService playerService;

    @Test
    void getPlayer() throws Exception {
        String id = NanoIdUtils.randomNanoId();
        PlayerDto dto = new PlayerDto();
        dto.setId(id);
        dto.setFirstName("Test");
        dto.setLastName("Test");

        Mockito.when(playerService.get(any())).thenReturn(dto);

        mockMvc.perform(get("/v1/players/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value(dto.getFirstName()))
            .andExpect(jsonPath("$.lastName").value(dto.getLastName()))
            .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getUnknownPlayer_expectNotFound() throws Exception {
        String id = NanoIdUtils.randomNanoId();
        Mockito.when(playerService.get(any())).thenThrow(new PlayerNotFoundException(id));

        mockMvc.perform(get("/v1/players/" + id))
            .andExpect(status().isNotFound());
    }
}
