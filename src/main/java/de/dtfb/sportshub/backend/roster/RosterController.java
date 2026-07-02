package de.dtfb.sportshub.backend.roster;

import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Roster of a team participation (L2): list players, add/remove them (team side, DRAFT + open
 * registration), and drive the roster lifecycle — submit (team), confirm/reopen (admin). Reads open;
 * writes gated by {@code @authz.canEditRoster} (team_admin or admin) and lifecycle approval by
 * {@code @authz.canConfirmRoster} (admin above the team only).
 */
@RestController
@RequestMapping("/v1/team-participations/{participationId}/roster")
public class RosterController {

    private final RosterService service;

    public RosterController(RosterService service) {
        this.service = service;
    }

    @GetMapping
    public List<RosterEntryDto> getRoster(@PathVariable String participationId) {
        return service.getRoster(participationId);
    }

    @PostMapping
    @PreAuthorize("@authz.canEditRoster(#participationId)")
    public ResponseEntity<RosterEntryDto> addPlayer(@PathVariable String participationId,
                                                    @RequestBody RosterEntryDto body) {
        RosterEntryDto created = service.addPlayer(participationId, body.getPlayerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{playerId}")
    @PreAuthorize("@authz.canEditRoster(#participationId)")
    public void removePlayer(@PathVariable String participationId, @PathVariable String playerId) {
        service.removePlayer(participationId, playerId);
    }

    @PostMapping("/submit")
    @PreAuthorize("@authz.canEditRoster(#participationId)")
    public TeamParticipationDto submit(@PathVariable String participationId) {
        return service.submit(participationId);
    }

    @PostMapping("/confirm")
    @PreAuthorize("@authz.canConfirmRoster(#participationId)")
    public TeamParticipationDto confirm(@PathVariable String participationId) {
        return service.confirm(participationId);
    }

    @PostMapping("/reopen")
    @PreAuthorize("@authz.canConfirmRoster(#participationId)")
    public TeamParticipationDto reopen(@PathVariable String participationId) {
        return service.reopen(participationId);
    }
}
