package de.dtfb.sportshub.backend.event;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    public List<EventDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<EventDto> create(@RequestBody EventDto eventDto) {
        EventDto eDto = service.create(eventDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + eDto.getUuid().toString()).build().toUri();

        return ResponseEntity.created(location).body(eDto);
    }

    @GetMapping("/{uuid}")
    public EventDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public EventDto update(@PathVariable String uuid, @RequestBody EventDto eventDto) {
        return service.update(uuid, eventDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
