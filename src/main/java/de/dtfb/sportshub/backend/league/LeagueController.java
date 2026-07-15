package de.dtfb.sportshub.backend.league;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/leagues")
public class LeagueController {

    private final LeagueService service;
    private final LeagueStructureService structureService;

    public LeagueController(LeagueService service, LeagueStructureService structureService) {
        this.service = service;
        this.structureService = structureService;
    }

    @GetMapping
    public List<LeagueDto> getAllLeagues() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canManageSeason(#leagueDto.seasonId)")
    public ResponseEntity<LeagueDto> createLeague(@RequestBody LeagueDto leagueDto) {
        LeagueDto returnedDto = service.create(leagueDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public LeagueDto getLeague(@PathVariable String id) {
        return service.get(id);
    }

    /**
     * The league's Tier -> Group subtree with a participation count per group -- the read behind the
     * placement board. Gated like league management (region admin above it): this feeds add/move/remove
     * of placements, not public viewing.
     */
    @GetMapping("/{id}/structure")
    @PreAuthorize("@authz.canManageLeague(#id)")
    public LeagueStructureDto getLeagueStructure(@PathVariable String id) {
        return structureService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageLeague(#id)")
    public LeagueDto updateLeague(@PathVariable String id, @RequestBody LeagueDto leagueDto) {
        return service.update(id, leagueDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canManageLeague(#id)")
    public void deleteLeague(@PathVariable String id) {
        service.delete(id);
    }
}
