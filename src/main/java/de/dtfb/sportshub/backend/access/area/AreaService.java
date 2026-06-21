package de.dtfb.sportshub.backend.access.area;

import de.dtfb.sportshub.backend.access.roleassignment.AccessRoles;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.player.Player;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the navigable areas (admin / region / club) a player may enter, expanding
 * the role→scope hierarchy server-side. A "region" is a {@link Federation}.
 */
@Service
public class AreaService {

    private static final String ADMIN_AREA_NAME = "DTFB Administration";

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final FederationRepository federationRepository;
    private final ClubRepository clubRepository;

    public AreaService(RoleAssignmentRepository roleAssignmentRepository,
                       FederationRepository federationRepository,
                       ClubRepository clubRepository) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.federationRepository = federationRepository;
        this.clubRepository = clubRepository;
    }

    @Transactional(readOnly = true)
    public MeAreasResponseDto getAreas(Player player) {
        List<RoleAssignment> roles = roleAssignmentRepository.findByPlayer(player);
        Map<String, AreaDto> areas = new LinkedHashMap<>();

        if (AccessRoles.isGlobalAdmin(roles)) {
            put(areas, adminArea());
            federationRepository.findAll().forEach(f -> put(areas, regionArea(f)));
            clubRepository.findAll().forEach(c -> put(areas, clubArea(c)));
            return new MeAreasResponseDto(new ArrayList<>(areas.values()));
        }

        for (RoleAssignment role : roles) {
            switch (role.getScopeType()) {
                case GLOBAL -> put(areas, adminArea());
                case REGION -> {
                    federationRepository.findById(role.getScopeId()).ifPresent(f -> put(areas, regionArea(f)));
                    // hierarchy: a federation role also grants the clubs inside that federation
                    clubRepository.findByFederationId(role.getScopeId()).forEach(c -> put(areas, clubArea(c)));
                }
                case CLUB -> clubRepository.findById(role.getScopeId()).ifPresent(c -> put(areas, clubArea(c)));
                case TEAM -> {
                    // Teams are not modelled in the access domain → no derivable area.
                }
            }
        }
        return new MeAreasResponseDto(new ArrayList<>(areas.values()));
    }

    private void put(Map<String, AreaDto> areas, AreaDto area) {
        areas.putIfAbsent(area.type() + "|" + area.id(), area);
    }

    private AreaDto adminArea() {
        return new AreaDto("admin", null, ADMIN_AREA_NAME, null, null);
    }

    private AreaDto regionArea(Federation federation) {
        return new AreaDto("region", federation.getId(), federation.getName(), federation.getId(), federation.getName());
    }

    private AreaDto clubArea(Club club) {
        String regionName = federationRepository.findById(club.getFederationId()).map(Federation::getName).orElse(null);
        return new AreaDto("club", club.getId(), club.getName(), club.getFederationId(), regionName);
    }
}
