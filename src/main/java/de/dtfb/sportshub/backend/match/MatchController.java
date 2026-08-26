package de.dtfb.sportshub.backend.match;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/matches")
public class MatchController {

    private final MatchService service;

    public MatchController(MatchService service) {
        this.service = service;
    }

    @GetMapping
    public List<MatchDto> getAllMatches() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canOrganizeMatchDay(#matchDto.matchDayId)")
    public ResponseEntity<MatchDto> createMatch(@RequestBody MatchDto matchDto) {
        MatchDto returnedDto = service.create(matchDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public MatchDto getMatch(@PathVariable String id) {
        return service.get(id);
    }

    // Also reachable by a write-capable registered app (e.g. a live-scoring integration) with an
    // in-scope ApiClientGrant -- see docs/10-api-consumers-and-authz.md §5, the one wired example
    // of the app-level (not human-role) authorization path. apiClientAuthz must be checked FIRST:
    // it always returns cleanly (false for a human/non-granted azp), whereas @authz.canOrganizeMatch
    // throws for a token with no dtfb_id (a service-account JWT) rather than returning false --
    // SpEL "or" short-circuits left-to-right, so throwing on the left would never let a legitimate
    // API-client grant on the right get evaluated.
    @PutMapping("/{id}")
    @PreAuthorize("@apiClientAuthz.canOrganizeMatch(#id) or @authz.canOrganizeMatch(#id)")
    public MatchDto updateMatch(@PathVariable String id, @RequestBody MatchDto matchDto) {
        return service.update(id, matchDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canOrganizeMatch(#id)")
    public void deleteMatch(@PathVariable String id) {
        service.delete(id);
    }
}
