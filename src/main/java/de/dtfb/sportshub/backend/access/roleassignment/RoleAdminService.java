package de.dtfb.sportshub.backend.access.roleassignment;

import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubDto;
import de.dtfb.sportshub.backend.club.ClubMapper;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationDto;
import de.dtfb.sportshub.backend.federation.FederationMapper;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserNotFoundException;
import de.dtfb.sportshub.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Role administration: listing, granting and revoking {@link RoleAssignment}s, plus the
 * "where may I grant?" scopes. Field mapping is delegated to {@link RoleAssignmentMapper};
 * the cross-entity names/ids an assignment only references by id (granter, scope) are
 * batch-loaded here once per request and passed into the mapper.
 */
@Service
public class RoleAdminService {

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final UserRepository userRepository;
    private final FederationRepository federationRepository;
    private final ClubRepository clubRepository;
    private final LeagueRepository leagueRepository;
    private final RoleAssignmentMapper mapper;
    private final FederationMapper federationMapper;
    private final ClubMapper clubMapper;

    public RoleAdminService(RoleAssignmentRepository roleAssignmentRepository,
                            UserRepository userRepository,
                            FederationRepository federationRepository,
                            ClubRepository clubRepository,
                            LeagueRepository leagueRepository,
                            RoleAssignmentMapper mapper,
                            FederationMapper federationMapper,
                            ClubMapper clubMapper) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.userRepository = userRepository;
        this.federationRepository = federationRepository;
        this.clubRepository = clubRepository;
        this.leagueRepository = leagueRepository;
        this.mapper = mapper;
        this.federationMapper = federationMapper;
        this.clubMapper = clubMapper;
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentDto> myRoleDtos(User user) {
        return toDtos(roleAssignmentRepository.findByUser(user));
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentDto> rolesForUser(String userId) {
        return userRepository.findById(userId)
            .map(this::myRoleDtos)
            .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentViewDto> assignments(Role role, String regionId, String q, String userId) {
        List<RoleAssignment> rows = findFiltered(role, userId);
        Map<String, Club> clubs = clubsByScopeId(rows);
        Map<String, Federation> federations = federationsByScopeId(rows);
        Map<String, League> leagues = leaguesByScopeId(rows);
        Map<String, User> granters = grantersByDtfbId(rows);
        String needle = q == null ? null : q.trim().toLowerCase();

        return rows.stream()
            .filter(ra -> regionId == null || matchesRegion(ra, regionId, clubs))
            .filter(ra -> needle == null || needle.isEmpty() || matchesQuery(ra, needle))
            .map(ra -> mapper.toViewDto(ra, scopeName(ra, federations, clubs, leagues), granterName(ra, granters)))
            .toList();
    }

    @Transactional(readOnly = true)
    public GrantableScopesDto grantableScopes(User user) {
        List<RoleAssignment> roles = roleAssignmentRepository.findByUser(user);

        if (AccessRoles.isGlobalAdmin(roles)) {
            List<FederationDto> regions = federationRepository.findAll().stream().map(federationMapper::toDto).toList();
            List<ClubDto> clubs = clubRepository.findAll().stream().map(clubMapper::toDto).toList();
            return new GrantableScopesDto(true, regions, clubs);
        }

        Map<String, FederationDto> regions = new LinkedHashMap<>();
        Map<String, ClubDto> clubs = new LinkedHashMap<>();
        for (RoleAssignment role : roles) {
            switch (role.getScopeType()) {
                case REGION -> {
                    federationRepository.findById(role.getScopeId())
                        .ifPresent(f -> regions.put(f.getId(), federationMapper.toDto(f)));
                    clubRepository.findByFederationId(role.getScopeId())
                        .forEach(c -> clubs.put(c.getId(), clubMapper.toDto(c)));
                }
                case CLUB -> clubRepository.findById(role.getScopeId())
                    .ifPresent(c -> clubs.put(c.getId(), clubMapper.toDto(c)));
                default -> {
                    // GLOBAL/TEAM contribute no grantable region/club scopes here.
                }
            }
        }
        return new GrantableScopesDto(false, new ArrayList<>(regions.values()), new ArrayList<>(clubs.values()));
    }

    @Transactional
    public RoleAssignmentDto grant(GrantRoleDto dto, String grantedByDtfbId) {
        User user = userRepository.findById(dto.userId())
            .orElseThrow(() -> new UserNotFoundException(dto.userId()));

        ScopeType scopeType = dto.role().scopeType();
        String requestedScopeId = scopeType == ScopeType.GLOBAL ? null : dto.scopeId();
        // A league admin administers the league across seasons (SPO-28): store the league identity,
        // whether the client sent a league row id or the identity itself.
        String scopeId = scopeType == ScopeType.LEAGUE
            ? leagueRepository.findByScopeId(requestedScopeId).map(League::getLeagueIdentityId).orElse(requestedScopeId)
            : requestedScopeId;

        // Idempotent: a matching grant already present is returned as-is.
        Optional<RoleAssignment> existing = roleAssignmentRepository.findByUser(user).stream()
            .filter(ra -> ra.getRole() == dto.role() && Objects.equals(ra.getScopeId(), scopeId))
            .findFirst();
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        RoleAssignment assignment = new RoleAssignment();
        assignment.setUser(user);
        assignment.setRole(dto.role());
        assignment.setScopeType(scopeType);
        assignment.setScopeId(scopeId);
        assignment.setGrantedByDtfbId(grantedByDtfbId);
        assignment.setCreatedAt(Instant.now());
        return toDto(roleAssignmentRepository.save(assignment));
    }

    @Transactional
    public void revoke(String id) {
        if (!roleAssignmentRepository.existsById(id)) {
            throw new RoleAssignmentNotFoundException(id);
        }
        roleAssignmentRepository.deleteById(id);
    }

    // --- querying ------------------------------------------------------------

    private List<RoleAssignment> findFiltered(Role role, String userId) {
        if (role != null && userId != null) {
            return roleAssignmentRepository.findByRoleAndUser_Id(role, userId);
        }
        if (role != null) {
            return roleAssignmentRepository.findByRole(role);
        }
        if (userId != null) {
            return roleAssignmentRepository.findByUser_Id(userId);
        }
        return roleAssignmentRepository.findAll();
    }

    // --- batch loading (one query per referenced entity type) ----------------

    private Map<String, Club> clubsByScopeId(List<RoleAssignment> rows) {
        List<String> ids = scopeIds(rows, ScopeType.CLUB);
        return ids.isEmpty() ? Map.of()
            : clubRepository.findAllById(ids).stream().collect(Collectors.toMap(Club::getId, Function.identity()));
    }

    private Map<String, Federation> federationsByScopeId(List<RoleAssignment> rows) {
        List<String> ids = scopeIds(rows, ScopeType.REGION);
        return ids.isEmpty() ? Map.of()
            : federationRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Federation::getId, Function.identity()));
    }

    /** LEAGUE scope id (a league identity, SPO-28) -> the newest season's copy, for its name. */
    private Map<String, League> leaguesByScopeId(List<RoleAssignment> rows) {
        Map<String, League> leagues = new HashMap<>();
        for (String scopeId : scopeIds(rows, ScopeType.LEAGUE)) {
            leagueRepository.findByScopeId(scopeId).ifPresent(league -> leagues.put(scopeId, league));
        }
        return leagues;
    }

    private List<String> scopeIds(List<RoleAssignment> rows, ScopeType type) {
        return rows.stream()
            .filter(ra -> ra.getScopeType() == type && ra.getScopeId() != null)
            .map(RoleAssignment::getScopeId)
            .distinct()
            .toList();
    }

    private Map<String, User> grantersByDtfbId(List<RoleAssignment> rows) {
        List<String> dtfbIds = rows.stream()
            .map(RoleAssignment::getGrantedByDtfbId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        return dtfbIds.isEmpty() ? Map.of()
            : userRepository.findByDtfbIdIn(dtfbIds).stream()
            .collect(Collectors.toMap(User::getDtfbId, Function.identity()));
    }

    // --- mapping helpers -----------------------------------------------------

    private List<RoleAssignmentDto> toDtos(List<RoleAssignment> rows) {
        Map<String, User> granters = grantersByDtfbId(rows);
        return rows.stream().map(ra -> mapper.toDto(ra, granterId(ra, granters))).toList();
    }

    private RoleAssignmentDto toDto(RoleAssignment ra) {
        return toDtos(List.of(ra)).getFirst();
    }

    private String granterId(RoleAssignment ra, Map<String, User> granters) {
        User granter = granter(ra, granters);
        return granter == null ? null : granter.getId();
    }

    private String granterName(RoleAssignment ra, Map<String, User> granters) {
        User granter = granter(ra, granters);
        return granter == null ? null : displayName(granter);
    }

    private User granter(RoleAssignment ra, Map<String, User> granters) {
        return ra.getGrantedByDtfbId() == null ? null : granters.get(ra.getGrantedByDtfbId());
    }

    private boolean matchesRegion(RoleAssignment ra, String federationId, Map<String, Club> clubs) {
        return switch (ra.getScopeType()) {
            case REGION -> federationId.equals(ra.getScopeId());
            case CLUB -> {
                Club club = clubs.get(ra.getScopeId());
                yield club != null && federationId.equals(club.getFederationId());
            }
            default -> false;
        };
    }

    private boolean matchesQuery(RoleAssignment ra, String needle) {
        User u = ra.getUser();
        return contains(u.getFirstName(), needle)
            || contains(u.getLastName(), needle)
            || contains(u.getEmail(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private String scopeName(RoleAssignment ra, Map<String, Federation> federations, Map<String, Club> clubs,
                              Map<String, League> leagues) {
        return switch (ra.getScopeType()) {
            case REGION -> Optional.ofNullable(federations.get(ra.getScopeId())).map(Federation::getName).orElse(null);
            case CLUB -> Optional.ofNullable(clubs.get(ra.getScopeId())).map(Club::getName).orElse(null);
            case LEAGUE -> Optional.ofNullable(leagues.get(ra.getScopeId())).map(League::getName).orElse(null);
            // TEAM scope names need a Team lookup by identity id — added with the enforcement work.
            case GLOBAL, TEAM -> null;
        };
    }

    private String displayName(User u) {
        String name = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
            + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return name.isEmpty() ? u.getDtfbId() : name;
    }
}
