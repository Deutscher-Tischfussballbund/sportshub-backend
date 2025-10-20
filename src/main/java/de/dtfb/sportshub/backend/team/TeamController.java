package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/teams")
public class TeamController {
    private final PlayerService playerService;
    private final TeamService teamService;

    public TeamController(PlayerService playerService, TeamService teamService) {
        this.playerService = playerService;
        this.teamService = teamService;
    }

    // ==========================================================
    // ============== READ ENDPOINTS =============================
    // ==========================================================

    @GetMapping
    @PreAuthorize("hasAuthority('team_captain')")
    public List<Team> allTeams(Authentication authentication) {
        var jwt = (Jwt) authentication.getPrincipal();
        var userTeamId = jwt.getClaimAsString("captainOfTeam");

        if (StringUtils.isNotBlank(userTeamId) && Integer.parseInt(userTeamId) == 1) {
//            return teamService.getAllTeams();
            Team team = new Team();
            team.setId(1L);
            team.setName("some test");
            return List.of(team);
        } else {
            return new ArrayList<>();
        }
    }

    @GetMapping("/{id}")
    public Team teamById(@PathVariable Long id) {
        return teamService.getTeamById(id);
    }

    @GetMapping("/{id}/players")
    public List<Player> playersByTeam(@PathVariable Long id) {
        return playerService.getPlayersByTeam(id);
    }

    // ==========================================================
    // ============== WRITE ENDPOINTS ============================
    // ==========================================================

    @PostMapping
    @PreAuthorize("authentication.token.claims['captainOfTeam'] == #team.id")
    public Team createTeam(@RequestBody Team team) {
        return teamService.createTeam(team);
    }
}
