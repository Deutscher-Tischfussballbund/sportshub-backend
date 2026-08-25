package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.club.ClubDto;
import de.dtfb.sportshub.backend.clubmembership.ClubMembershipRepository;
import de.dtfb.sportshub.backend.clubmembership.ClubMembershipService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * The player directory: listing/search across every {@link Player} row, optionally scoped to a
 * club's or region's active members. Distinct from
 * {@link de.dtfb.sportshub.backend.user.UserRegistryService}, which resolves login identity, not
 * competitor records.
 */
@Service
public class PlayerDirectoryService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final ClubMembershipRepository membershipRepository;
    private final ClubMembershipService membershipService;

    public PlayerDirectoryService(PlayerRepository playerRepository, PlayerMapper playerMapper,
                                  ClubMembershipRepository membershipRepository,
                                  ClubMembershipService membershipService) {
        this.playerRepository = playerRepository;
        this.playerMapper = playerMapper;
        this.membershipRepository = membershipRepository;
        this.membershipService = membershipService;
    }

    /**
     * The player directory, optionally narrowed to a club's or region's active members and/or a
     * name/nationalId search. {@code clubId} takes precedence over {@code regionId} if both are
     * given (a club is always within one region already).
     */
    @Transactional(readOnly = true)
    public List<PlayerDto> findAll(String clubId, String regionId, String q) {
        List<Player> players = clubId != null
            ? playerRepository.findAllById(membershipRepository.activePlayerIdsForClub(clubId))
            : regionId != null
                ? playerRepository.findAllById(membershipRepository.activePlayerIdsForRegion(regionId))
                : playerRepository.findAll();
        if (q != null && !q.isBlank()) {
            players = players.stream().filter(p -> matches(p, q)).toList();
        }
        return withClubs(players);
    }

    /** Name/nationalId search across the whole player directory, unscoped by club/region. */
    @Transactional(readOnly = true)
    public List<PlayerDto> search(String q) {
        return withClubs(rawSearch(q));
    }

    private List<Player> rawSearch(String q) {
        return (q == null || q.isBlank())
            ? playerRepository.findAll()
            : playerRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrNationalIdContainingIgnoreCase(
                    q, q, q);
    }

    private boolean matches(Player p, String q) {
        String needle = q.toLowerCase();
        return contains(p.getFirstName(), needle) || contains(p.getLastName(), needle)
            || contains(p.getNationalId(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private List<PlayerDto> withClubs(List<Player> players) {
        List<PlayerDto> dtos = playerMapper.toDtoList(players);
        Map<String, List<ClubDto>> clubsByPlayerId =
            membershipService.clubsByPlayerId(dtos.stream().map(PlayerDto::getId).toList());
        dtos.forEach(dto -> dto.setClubs(clubsByPlayerId.getOrDefault(dto.getId(), List.of())));
        return dtos;
    }
}
