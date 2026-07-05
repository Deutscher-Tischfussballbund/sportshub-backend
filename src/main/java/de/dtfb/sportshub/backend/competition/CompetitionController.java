package de.dtfb.sportshub.backend.competition;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/competitions")
public class CompetitionController {

    private final CompetitionService service;
    private final CompetitionStructureService structureService;

    public CompetitionController(CompetitionService service, CompetitionStructureService structureService) {
        this.service = service;
        this.structureService = structureService;
    }

    @GetMapping
    public List<CompetitionDto> getAllCompetitions() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canManageSeason(#competitionDto.seasonId)")
    public ResponseEntity<CompetitionDto> createCompetition(@RequestBody CompetitionDto competitionDto) {
        CompetitionDto returnedDto = service.create(competitionDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public CompetitionDto getCompetition(@PathVariable String id) {
        return service.get(id);
    }

    /**
     * The competition's Discipline→Stage→Pool subtree with a participation count per pool — the read
     * behind the placement board. Gated like competition management (region admin above it): this feeds
     * add/move/remove of placements, not public viewing.
     */
    @GetMapping("/{id}/structure")
    @PreAuthorize("@authz.canManageCompetition(#id)")
    public CompetitionStructureDto getCompetitionStructure(@PathVariable String id) {
        return structureService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageCompetition(#id)")
    public CompetitionDto updateCompetition(@PathVariable String id, @RequestBody CompetitionDto competitionDto) {
        return service.update(id, competitionDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canManageCompetition(#id)")
    public void deleteCompetition(@PathVariable String id) {
        service.delete(id);
    }
}
