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

    public CompetitionController(CompetitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<CompetitionDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canManageSeason(#competitionDto.seasonId)")
    public ResponseEntity<CompetitionDto> create(@RequestBody CompetitionDto competitionDto) {
        CompetitionDto returnedDto = service.create(competitionDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public CompetitionDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageCompetition(#id)")
    public CompetitionDto update(@PathVariable String id, @RequestBody CompetitionDto competitionDto) {
        return service.update(id, competitionDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canManageCompetition(#id)")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
