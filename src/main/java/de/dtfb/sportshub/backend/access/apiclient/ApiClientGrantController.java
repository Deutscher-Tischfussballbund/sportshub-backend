package de.dtfb.sportshub.backend.access.apiclient;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Manages which registered apps/service clients may write, and within what scope -- see
 * {@link ApiClientGrant}. Global-admin-only end to end (unlike most read-open admin CRUD in this
 * app): this data controls write access for machine clients, not day-to-day domain content.
 */
@RestController
@RequestMapping("/v1/admin/api-clients")
public class ApiClientGrantController {

    private final ApiClientGrantService service;

    public ApiClientGrantController(ApiClientGrantService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@authz.isAdmin()")
    public List<ApiClientGrantDto> getAllGrants() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.isAdmin()")
    public ResponseEntity<ApiClientGrantDto> createGrant(@RequestBody ApiClientGrantDto dto) {
        ApiClientGrantDto returnedDto = service.create(dto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public ApiClientGrantDto updateGrant(@PathVariable String id, @RequestBody ApiClientGrantDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public void deleteGrant(@PathVariable String id) {
        service.delete(id);
    }
}
