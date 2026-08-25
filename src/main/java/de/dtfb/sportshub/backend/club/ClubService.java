package de.dtfb.sportshub.backend.club;

import de.dtfb.sportshub.backend.clubmembership.ClubMembershipRepository;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.history.ChangeSet;
import de.dtfb.sportshub.backend.history.EntityHistoryService;
import de.dtfb.sportshub.backend.history.HistoryEntityType;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClubService {

    private final ClubRepository repository;
    private final ClubMapper mapper;
    private final EntityHistoryService historyService;
    private final FederationRepository federationRepository;
    private final TeamRepository teamRepository;
    private final ClubMembershipRepository membershipRepository;

    public ClubService(ClubRepository repository, ClubMapper mapper, EntityHistoryService historyService,
                       FederationRepository federationRepository, TeamRepository teamRepository,
                       ClubMembershipRepository membershipRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.historyService = historyService;
        this.federationRepository = federationRepository;
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public List<ClubDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional
    public ClubDto create(ClubDto dto) {
        federationRepository.findById(dto.regionId())
            .orElseThrow(() -> new FederationNotFoundException(dto.regionId()));
        Club club = mapper.toEntity(dto);
        return mapper.toDto(repository.save(club));
    }

    /**
     * A club with teams and/or active members can't be hard-deleted -- both reference it by a
     * required FK, so deleting it out from under them would either fail or silently orphan real
     * data. Deactivate the club instead ({@link #update}, {@code active = false}).
     */
    @Transactional
    public void delete(String id) {
        Club club = repository.findById(id).orElseThrow(() -> new ClubNotFoundException(id));
        if (teamRepository.existsByClub_Id(id)) {
            throw new ClubDeletionBlockedException("Club still has one or more teams; deactivate it instead");
        }
        if (membershipRepository.existsByClub_IdAndLeftAtIsNull(id)) {
            throw new ClubDeletionBlockedException("Club still has active members; deactivate it instead");
        }
        repository.delete(club);
    }

    @Transactional
    public ClubDto update(String id, ClubDto dto, String changedByDtfbId) {
        Club club = repository.findById(id).orElseThrow(() -> new ClubNotFoundException(id));

        ChangeSet changes = ChangeSet.forEntity(HistoryEntityType.CLUB, id)
            .track("name", club.getName(), dto.name())
            .track("shortName", club.getShortName(), dto.shortName())
            .track("city", club.getCity(), dto.city())
            .track("active", club.isActive(), dto.active());

        club.setName(dto.name());
        club.setShortName(dto.shortName());
        club.setCity(dto.city());
        club.setActive(dto.active());

        historyService.record(changes, changedByDtfbId);
        return mapper.toDto(repository.save(club));
    }
}
