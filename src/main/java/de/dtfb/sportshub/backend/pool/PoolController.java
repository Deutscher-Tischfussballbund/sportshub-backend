package de.dtfb.sportshub.backend.pool;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pools")
public class PoolController {

    private final PoolService service;

    public PoolController(PoolService service) {
        this.service = service;
    }

    @GetMapping
    public List<PoolDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<PoolDto> create(@Valid @RequestBody PoolDto poolDto) {
        PoolDto returnedDto = service.create(poolDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId().toString()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public PoolDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public PoolDto update(@PathVariable String uuid, @RequestBody PoolDto poolDto) {
        return service.update(uuid, poolDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
