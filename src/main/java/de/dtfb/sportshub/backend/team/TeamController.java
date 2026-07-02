package de.dtfb.sportshub.backend.team;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/teams")
public class TeamController {

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    public List<TeamDto> getAllTeams() {
        return service.getAll();
    }

    @PostMapping
    // Admin of the target club (or its region / global). Every team belongs to a club
    // (the service rejects a missing clubId), so there is no clubless path here.
    @PreAuthorize("@authz.canManageClub(#teamDto.clubId)")
    public ResponseEntity<TeamDto> createTeam(@RequestBody TeamDto teamDto) {
        TeamDto returnedDto = service.create(teamDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public TeamDto getTeam(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageTeam(#id)")
    public TeamDto updateTeam(@PathVariable String id, @RequestBody TeamDto teamDto) {
        return service.update(id, teamDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canManageTeam(#id)")
    public void deleteTeam(@PathVariable String id) {
        service.delete(id);
    }
}
