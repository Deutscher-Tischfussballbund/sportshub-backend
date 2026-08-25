package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.history.EntityHistoryDto;
import de.dtfb.sportshub.backend.history.EntityHistoryService;
import de.dtfb.sportshub.backend.history.HistoryEntityType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-frontend player directory read/write (separate root path from the external-API {@link PlayerController}). */
@RestController
public class PlayerAdminController {

    private final PlayerDirectoryService directory;
    private final PlayerService playerService;
    private final EntityHistoryService historyService;

    public PlayerAdminController(PlayerDirectoryService directory, PlayerService playerService,
                                 EntityHistoryService historyService) {
        this.directory = directory;
        this.playerService = playerService;
        this.historyService = historyService;
    }

    // clubId/regionId narrow the directory to a club's/region's active members (via
    // ClubMembership); clubId wins if both are given. q is a name/nationalId search, orthogonal
    // to the scope filters.
    @GetMapping("/v1/admin/players")
    public List<PlayerDto> players(@RequestParam(required = false) String clubId,
                                   @RequestParam(required = false) String regionId,
                                   @RequestParam(required = false) String q) {
        return directory.findAll(clubId, regionId, q);
    }

    // Editing a player's profile is unrelated to which club(s) they're a member of, so this stays
    // global-admin only rather than gated per club.
    @PutMapping("/v1/admin/players/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public PlayerDto updatePlayer(@PathVariable String id, @RequestBody PlayerDto dto,
                                  @AuthenticationPrincipal Jwt jwt) {
        return playerService.update(id, dto, jwt.getClaimAsString("dtfb_id"));
    }

    @GetMapping("/v1/admin/players/{id}/history")
    @PreAuthorize("@authz.isAdmin()")
    public List<EntityHistoryDto> playerHistory(@PathVariable String id) {
        return historyService.history(HistoryEntityType.PLAYER, id);
    }
}
