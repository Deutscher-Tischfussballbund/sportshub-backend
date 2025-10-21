package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.player.PlayerDto;
import de.dtfb.sportshub.backend.player.PlayerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("hasAuthority('admin')")
    public List<TeamDto> allTeams() {
        return teamService.getAllTeams();
    }

    @GetMapping("/{id}")
    public TeamDto teamById(@PathVariable Long id) {
        return teamService.getTeamById(id);
    }

    @GetMapping("/{id}/players")
    public List<PlayerDto> playersByTeam(@PathVariable Long id) {
        return playerService.getPlayersByTeam(id);
    }

    // ==========================================================
    // ============== WRITE ENDPOINTS ============================
    // ==========================================================

    @PostMapping
    @PreAuthorize("hasAuthority('admin')")
    public TeamDto createTeam(@RequestBody TeamDto teamDto) {
        return teamService.createTeam(teamDto);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('admin') || @teamSecurity.canManage(authentication, #teamDto.id)")
    public TeamDto updateTeam(@RequestBody TeamDto teamDto) {
        return teamService.updateTeam(teamDto);
    }
}
