package de.dtfb.sportshub.backend.group;

import de.dtfb.sportshub.backend.round.FixtureGenerationService;
import de.dtfb.sportshub.backend.round.GenerateFixturesRequest;
import de.dtfb.sportshub.backend.round.RoundDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/groups")
public class GroupController {

    private final GroupService service;
    private final FixtureGenerationService fixtureGenerationService;

    public GroupController(GroupService service, FixtureGenerationService fixtureGenerationService) {
        this.service = service;
        this.fixtureGenerationService = fixtureGenerationService;
    }

    @GetMapping
    public List<GroupDto> getAllGroups() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canOrganizeTier(#groupDto.tierId)")
    public ResponseEntity<GroupDto> createGroup(@Valid @RequestBody GroupDto groupDto) {
        GroupDto returnedDto = service.create(groupDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public GroupDto getGroup(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canOrganizeGroup(#id)")
    public GroupDto updateGroup(@PathVariable String id, @RequestBody GroupDto groupDto) {
        return service.update(id, groupDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canOrganizeGroup(#id)")
    public void deleteGroup(@PathVariable String id) {
        service.delete(id);
    }

    @PostMapping("/{id}/fixtures/generate")
    @PreAuthorize("@authz.canOrganizeGroup(#id)")
    public List<RoundDto> generateFixtures(@PathVariable String id, @RequestBody GenerateFixturesRequest request) {
        return fixtureGenerationService.generate(id, request);
    }
}
