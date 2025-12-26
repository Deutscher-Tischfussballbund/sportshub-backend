package de.dtfb.sportshub.backend.discipline;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/disciplines")
public class DisciplineController {

    private final DisciplineService service;

    public DisciplineController(DisciplineService service) {
        this.service = service;
    }

    @GetMapping
    public List<DisciplineDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<DisciplineDto> create(@RequestBody DisciplineDto disciplineDto) {
        DisciplineDto returnedDto = service.create(disciplineDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getUuid().toString()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public DisciplineDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public DisciplineDto update(@PathVariable String uuid, @RequestBody DisciplineDto eventDto) {
        return service.update(uuid, eventDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
