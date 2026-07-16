package de.dtfb.sportshub.backend.teamparticipation;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/team-participations")
public class TeamParticipationController {

    private final TeamParticipationService service;

    public TeamParticipationController(TeamParticipationService service) {
        this.service = service;
    }

    @GetMapping
    public List<TeamParticipationDto> getAllTeamParticipations(@RequestParam(required = false) String seasonId,
                                             @RequestParam(required = false) String leagueId,
                                             @RequestParam(required = false) String teamId) {
        return service.getAll(seasonId, leagueId, teamId);
    }

    @PostMapping
    @PreAuthorize("@authz.canManageLeague(#teamParticipationDto.leagueId)")
    public ResponseEntity<TeamParticipationDto> createTeamParticipation(@RequestBody TeamParticipationDto teamParticipationDto) {
        TeamParticipationDto returnedDto = service.create(teamParticipationDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public TeamParticipationDto getTeamParticipation(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageParticipation(#id)")
    public TeamParticipationDto updateTeamParticipation(@PathVariable String id, @RequestBody TeamParticipationDto teamParticipationDto) {
        return service.update(id, teamParticipationDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canManageParticipation(#id)")
    public void deleteTeamParticipation(@PathVariable String id) {
        service.delete(id);
    }
}
