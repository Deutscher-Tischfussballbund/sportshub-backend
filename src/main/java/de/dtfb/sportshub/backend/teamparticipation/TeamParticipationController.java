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

    /** A region admin's approval queue: rosters submitted for confirmation in their federation. */
    @GetMapping("/pending")
    @PreAuthorize("@authz.canManageRegion(#federationId)")
    public List<TeamParticipationDto> getPendingTeamParticipations(@RequestParam String federationId) {
        return service.getPendingApprovals(federationId);
    }

    @PostMapping
    @PreAuthorize("@authz.canRegisterForLeague(#teamParticipationDto.leagueId, #teamParticipationDto.teamId)")
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

    /** The team drops out of the league for the rest of the season -- a status change, not a delete. */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@authz.canManageParticipation(#id)")
    public TeamParticipationDto withdrawTeamParticipation(@PathVariable String id) {
        return service.withdraw(id);
    }
}
