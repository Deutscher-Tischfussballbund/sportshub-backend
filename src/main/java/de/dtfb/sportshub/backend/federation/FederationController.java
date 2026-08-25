package de.dtfb.sportshub.backend.federation;

import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/federation")
public class FederationController {

    private final FederationService service;
    private final AuthorizationService authz;

    public FederationController(FederationService service, AuthorizationService authz) {
        this.service = service;
        this.authz = authz;
    }

    @GetMapping
    public List<FederationDto> getAllFederations() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.isAdmin()")
    public ResponseEntity<FederationDto> createFederation(@RequestBody FederationDto federationDto) {
        FederationDto returnedDto = service.create(federationDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public FederationDto getFederation(@PathVariable String id) {
        return service.get(id);
    }

    /**
     * Open to a region admin managing their own federation (e.g. picking its default rule set,
     * from the rule-set dialog), not just global admins -- {@code FederationService#update} still
     * refuses a parent-federation change unless the caller is a global admin.
     */
    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageRegion(#id)")
    public FederationDto updateFederation(@PathVariable String id, @RequestBody FederationDto federationDto) {
        return service.update(id, federationDto, authz.isAdmin());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public void deleteFederation(@PathVariable String id) {
        service.delete(id);
    }
}
