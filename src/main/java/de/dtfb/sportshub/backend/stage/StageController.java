package de.dtfb.sportshub.backend.stage;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/stages")
public class StageController {

    private final StageService service;

    public StageController(StageService service) {
        this.service = service;
    }

    @GetMapping
    public List<StageDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<StageDto> create(@RequestBody StageDto stageDto) {
        StageDto returnedDto = service.create(stageDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public StageDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public StageDto update(@PathVariable String id, @RequestBody StageDto stageDto) {
        return service.update(id, stageDto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
