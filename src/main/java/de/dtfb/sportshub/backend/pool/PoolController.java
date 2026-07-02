package de.dtfb.sportshub.backend.pool;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/pools")
public class PoolController {

    private final PoolService service;

    public PoolController(PoolService service) {
        this.service = service;
    }

    @GetMapping
    public List<PoolDto> getAllPools() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canOrganizeStage(#poolDto.stageId)")
    public ResponseEntity<PoolDto> createPool(@Valid @RequestBody PoolDto poolDto) {
        PoolDto returnedDto = service.create(poolDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public PoolDto getPool(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canOrganizePool(#id)")
    public PoolDto updatePool(@PathVariable String id, @RequestBody PoolDto poolDto) {
        return service.update(id, poolDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canOrganizePool(#id)")
    public void deletePool(@PathVariable String id) {
        service.delete(id);
    }
}
