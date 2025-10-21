package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.team.membership.TeamMembership;
import de.dtfb.sportshub.backend.team.membership.TeamMembershipEnum;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("teamSecurity")
public class TeamSecurity {

    private final TeamService teamService;

    public TeamSecurity(TeamService teamService) {
        this.teamService = teamService;
    }

    /**
     * Checks if the authenticated user is allowed to manage a specific team.
     */
    public boolean canManage(Authentication authentication, Long teamId) {
        var userId = authentication.getName(); // from JWT 'sub'

        Optional<Long> numericUserId = Optional.of(Long.valueOf(userId));
        TeamMembership membership = teamService.getMembership(teamId, TeamMembershipEnum.CAPTAIN);

        return membership != null && numericUserId.map(id -> id.equals(membership.getPlayerId())).orElse(false);
    }
}
