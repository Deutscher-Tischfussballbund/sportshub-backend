package de.dtfb.sportshub.backend.matchevent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/matchevents")
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

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getUuid().toString()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public MatchEventDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public MatchEventDto update(@PathVariable String uuid, @RequestBody MatchEventDto matchEventDto) {
        return service.update(uuid, matchEventDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
