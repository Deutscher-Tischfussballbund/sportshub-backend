package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.club.ClubDto;
import de.dtfb.sportshub.backend.clubmembership.ClubMembershipService;
import de.dtfb.sportshub.backend.history.ChangeSet;
import de.dtfb.sportshub.backend.history.EntityHistoryService;
import de.dtfb.sportshub.backend.history.HistoryEntityType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final EntityHistoryService historyService;
    private final ClubMembershipService membershipService;
    private final PlayerGenderVisibility genderVisibility;

    public PlayerService(PlayerRepository playerRepository, PlayerMapper playerMapper,
                         EntityHistoryService historyService, ClubMembershipService membershipService,
                         PlayerGenderVisibility genderVisibility) {
        this.playerRepository = playerRepository;
        this.playerMapper = playerMapper;
        this.historyService = historyService;
        this.membershipService = membershipService;
        this.genderVisibility = genderVisibility;
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
        requireMandatoryFields(dto);

        ChangeSet changes = ChangeSet.forEntity(HistoryEntityType.PLAYER, id)
            .track("firstName", player.getFirstName(), dto.getFirstName())
            .track("lastName", player.getLastName(), dto.getLastName())
            .track("nationality", player.getNationality(), dto.getNationality())
            .track("birthYear", player.getBirthYear(), dto.getBirthYear())
            .track("gender", player.getGender(), dto.getGenderDetail())
            .track("nationalLicense", player.getNationalLicense(), dto.getNationalLicense());

        player.setFirstName(dto.getFirstName());
        player.setLastName(dto.getLastName());
        player.setNationality(dto.getNationality());
        player.setBirthYear(dto.getBirthYear());
        // genderDetail (with the divers league side) is the writable field; the public gender is derived.
        player.setGender(dto.getGenderDetail());
        player.setNationalLicense(dto.getNationalLicense());

        historyService.record(changes, changedByDtfbId);
        return withClubs(playerMapper.toDto(playerRepository.save(player)));
    }

    /**
     * First/last name, birth year and gender are mandatory (NOT NULL since V11) -- validated here
     * rather than via bean-validation annotations on {@link PlayerDto}, since {@code genderDetail}
     * is deliberately null on reads for non-admins and must not be marked required in the schema.
     */
    private static void requireMandatoryFields(PlayerDto dto) {
        if (isBlank(dto.getFirstName()) || isBlank(dto.getLastName())
            || dto.getBirthYear() == null || dto.getGenderDetail() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "firstName, lastName, birthYear and genderDetail are required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private PlayerDto withClubs(PlayerDto dto) {
        List<ClubDto> clubs = membershipService.clubsByPlayerId(List.of(dto.getId())).getOrDefault(dto.getId(), List.of());
        dto.setClubs(clubs);
        genderVisibility.apply(List.of(dto));
        return dto;
    }
}
