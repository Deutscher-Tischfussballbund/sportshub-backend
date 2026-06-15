package de.dtfb.sportshub.backend.standing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pools")
public class StandingController {

    private final StandingService standingService;

    public StandingController(StandingService standingService) {
        this.standingService = standingService;
    }

    @GetMapping("/{uuid}/standings")
    public List<StandingDto> getStandings(@PathVariable String uuid) {
        return standingService.getByPool(uuid);
    }
}
