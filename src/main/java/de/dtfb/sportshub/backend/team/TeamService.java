package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubNotFoundException;
import de.dtfb.sportshub.backend.club.ClubRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamService {
    private final TeamRepository repository;
    private final TeamMapper mapper;
    private final ClubRepository clubRepository;

    public TeamService(TeamRepository repository, TeamMapper mapper, ClubRepository clubRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.clubRepository = clubRepository;
    }

    @Transactional(readOnly = true)
    public List<TeamDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public TeamDto get(String id) {
        Team team = repository.findById(id).orElseThrow(
            () -> new TeamNotFoundException(id));
        return mapper.toDto(team);
    }

    @Transactional
    public TeamDto create(TeamDto teamDto) {
        Team newTeam = mapper.toEntity(teamDto);
        resolveClub(teamDto, newTeam);
        Team savedTeam = repository.save(newTeam);
        return mapper.toDto(savedTeam);
    }

    @Transactional
    public TeamDto update(String id, TeamDto teamDto) {
        Team team = repository.findById(id).orElseThrow(
            () -> new TeamNotFoundException(id));

        mapper.updateEntityFromDto(teamDto, team);
        resolveClub(teamDto, team);

        Team savedTeam = repository.save(team);
        return mapper.toDto(savedTeam);
    }

    @Transactional
    public void delete(String id) {
        Team team = repository.findById(id).orElseThrow(
            () -> new TeamNotFoundException(id));
        repository.delete(team);
    }

    private void resolveClub(TeamDto dto, Team team) {
        if (dto.getClubId() != null) {
            Club club = clubRepository.findById(dto.getClubId())
                .orElseThrow(() -> new ClubNotFoundException(dto.getClubId()));
            team.setClub(club);
        }
    }
}
