package de.dtfb.sportshub.backend.matchevent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/matchevents")
public class MatchEventController {

    private final MatchEventService service;

    public MatchEventController(MatchEventService service) {
        this.service = service;
    }

    @GetMapping
    public List<MatchEventDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<MatchEventDto> create(@RequestBody MatchEventDto matchEventDto) {
        MatchEventDto returnedDto = service.create(matchEventDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public MatchEventDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public MatchEventDto update(@PathVariable String id, @RequestBody MatchEventDto matchEventDto) {
        return service.update(id, matchEventDto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
