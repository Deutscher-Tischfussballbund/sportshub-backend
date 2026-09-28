package de.dtfb.sportshub.backend.standing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/groups")
public class StandingController {

    private final StandingService standingService;

    public StandingController(StandingService standingService) {
        this.standingService = standingService;
    }

    /**
     * The group's table. Default: the official one (final results only). {@code provisional=true}: the
     * live one, which also counts entered but not yet confirmed results, game by game (docs/17).
     */
    @GetMapping("/{id}/standings")
    public List<StandingDto> getStandings(@PathVariable String id,
                                          @RequestParam(defaultValue = "false") boolean provisional) {
        return provisional ? standingService.getProvisionalByGroup(id) : standingService.getByGroup(id);
    }
}
