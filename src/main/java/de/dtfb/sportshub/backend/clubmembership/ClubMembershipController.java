package de.dtfb.sportshub.backend.clubmembership;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Club membership: which players belong to a club. The member LIST is just
 * {@code GET /v1/admin/players?clubId=...} (see PlayerAdminController) -- this controller only
 * owns the join/leave actions.
 */
@RestController
public class ClubMembershipController {

    private final ClubMembershipService service;

    public ClubMembershipController(ClubMembershipService service) {
        this.service = service;
    }

    @PostMapping("/v1/admin/clubs/{clubId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authz.canManageClub(#clubId)")
    public void addClubMember(@PathVariable String clubId, @RequestBody AddClubMemberDto dto) {
        service.join(dto.playerId(), clubId);
    }

    @DeleteMapping("/v1/admin/clubs/{clubId}/members/{playerId}")
    @PreAuthorize("@authz.canManageClub(#clubId)")
    public void removeClubMember(@PathVariable String clubId, @PathVariable String playerId) {
        service.leave(playerId, clubId);
    }
}
