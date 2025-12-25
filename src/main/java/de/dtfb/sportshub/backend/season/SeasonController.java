package de.dtfb.sportshub.backend.season;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seasons")
public class SeasonController {

    private final SeasonService seasonService;

    public SeasonController(SeasonService seasonService) {
        this.seasonService = seasonService;
    }

    @GetMapping
    public List<SeasonDto> getAllSeasons() {
        return seasonService.getAllSeasons();
    }

    @PostMapping
    public ResponseEntity<SeasonDto> createSeason(@RequestBody SeasonDto seasonDto) {
        SeasonDto sDto = seasonService.createSeason(seasonDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + sDto.getUuid().toString()).build().toUri();

        return ResponseEntity.created(location).body(sDto);
    }

    @GetMapping("/{uuid}")
    public SeasonDto getSeason(@PathVariable String uuid) {
        return seasonService.getSeason(uuid);
    }

    @PutMapping("/{uuid}")
    public SeasonDto updateSeason(@PathVariable String uuid, @RequestBody SeasonDto seasonDto) {
        return seasonService.updateSeason(uuid, seasonDto);
    }

    @DeleteMapping("/{uuid}")
    public void deleteSeason(@PathVariable String uuid) {
        seasonService.deleteSeason(uuid);
    }
}
