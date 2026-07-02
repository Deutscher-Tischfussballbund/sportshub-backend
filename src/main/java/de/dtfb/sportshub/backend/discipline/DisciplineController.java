package de.dtfb.sportshub.backend.discipline;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/disciplines")
public class DisciplineController {

    private final DisciplineService service;

    public DisciplineController(DisciplineService service) {
        this.service = service;
    }

    @GetMapping
    public List<DisciplineDto> getAllDisciplines() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canManageCompetition(#disciplineDto.competitionId)")
    public ResponseEntity<DisciplineDto> createDiscipline(@RequestBody DisciplineDto disciplineDto) {
        DisciplineDto returnedDto = service.create(disciplineDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public DisciplineDto getDiscipline(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageDiscipline(#id)")
    public DisciplineDto updateDiscipline(@PathVariable String id, @RequestBody DisciplineDto eventDto) {
        return service.update(id, eventDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canManageDiscipline(#id)")
    public void deleteDiscipline(@PathVariable String id) {
        service.delete(id);
    }
}
