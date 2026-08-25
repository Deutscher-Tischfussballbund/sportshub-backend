package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.club.ClubDto;
import de.dtfb.sportshub.backend.clubmembership.ClubMembershipService;
import de.dtfb.sportshub.backend.history.ChangeSet;
import de.dtfb.sportshub.backend.history.EntityHistoryService;
import de.dtfb.sportshub.backend.history.HistoryEntityType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final EntityHistoryService historyService;
    private final ClubMembershipService membershipService;

    public PlayerService(PlayerRepository playerRepository, PlayerMapper playerMapper,
                         EntityHistoryService historyService, ClubMembershipService membershipService) {
        this.playerRepository = playerRepository;
        this.playerMapper = playerMapper;
        this.historyService = historyService;
        this.membershipService = membershipService;
    }

    @Transactional(readOnly = true)
    public PlayerDto get(String id) {
        Player player = playerRepository.findById(id).orElseThrow(() -> new PlayerNotFoundException(id));
        return withClubs(playerMapper.toDto(player));
    }

    /**
     * Edits the given player's profile fields, recording a history entry for each one that
     * actually changed -- so a past display (e.g. a roster from before the change) can still be
     * reconstructed with the value it had back then. {@code nationalId}/{@code internationalId}/
     * {@code active} are external-system identifiers and stay read-only here.
     */
    @Transactional
    public PlayerDto update(String id, PlayerDto dto, String changedByDtfbId) {
        Player player = playerRepository.findById(id).orElseThrow(() -> new PlayerNotFoundException(id));

        ChangeSet changes = ChangeSet.forEntity(HistoryEntityType.PLAYER, id)
            .track("firstName", player.getFirstName(), dto.getFirstName())
            .track("lastName", player.getLastName(), dto.getLastName())
            .track("nationality", player.getNationality(), dto.getNationality())
            .track("birthYear", player.getBirthYear(), dto.getBirthYear())
            .track("gender", player.getGender(), dto.getGender())
            .track("nationalLicense", player.getNationalLicense(), dto.getNationalLicense());

        player.setFirstName(dto.getFirstName());
        player.setLastName(dto.getLastName());
        player.setNationality(dto.getNationality());
        player.setBirthYear(dto.getBirthYear());
        player.setGender(dto.getGender());
        player.setNationalLicense(dto.getNationalLicense());

        historyService.record(changes, changedByDtfbId);
        return withClubs(playerMapper.toDto(playerRepository.save(player)));
    }

    private PlayerDto withClubs(PlayerDto dto) {
        List<ClubDto> clubs = membershipService.clubsByPlayerId(List.of(dto.getId())).getOrDefault(dto.getId(), List.of());
        dto.setClubs(clubs);
        return dto;
    }
}
