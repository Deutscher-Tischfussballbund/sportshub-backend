package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubNotFoundException;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.history.EntityHistoryService;
import de.dtfb.sportshub.backend.history.HistoryEntityType;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonNotFoundException;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class TeamService {
    private final TeamRepository repository;
    private final TeamMapper mapper;
    private final ClubRepository clubRepository;
    private final SeasonRepository seasonRepository;
    private final EntityHistoryService historyService;

    public TeamService(TeamRepository repository, TeamMapper mapper, ClubRepository clubRepository,
                       SeasonRepository seasonRepository, EntityHistoryService historyService) {
        this.repository = repository;
        this.mapper = mapper;
        this.clubRepository = clubRepository;
        this.seasonRepository = seasonRepository;
        this.historyService = historyService;
    }

    /**
     * With no {@code seasonId}, collapses every team's season-copies down to the latest one (by
     * {@code season.startDate}) -- the shape a "pick a team" list wants. Pass {@code seasonId} for
     * the rows that actually exist in one specific season.
     */
    @Transactional(readOnly = true)
    public List<TeamDto> getAll(String seasonId) {
        if (seasonId != null) {
            return toDtoListAsOf(repository.findBySeasonId(seasonId));
        }
        Map<String, Team> latestByIdentity = new LinkedHashMap<>();
        for (Team team : repository.findAll()) {
            Team existing = latestByIdentity.get(team.getTeamIdentityId());
            if (existing == null || isLater(team, existing)) {
                latestByIdentity.put(team.getTeamIdentityId(), team);
            }
        }
        return toDtoListAsOf(new ArrayList<>(latestByIdentity.values()));
    }

    private boolean isLater(Team candidate, Team current) {
        LocalDate candidateStart = candidate.getSeason() == null ? null : candidate.getSeason().getStartDate();
        LocalDate currentStart = current.getSeason() == null ? null : current.getSeason().getStartDate();
        return Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder())
            .compare(candidateStart, currentStart) > 0;
    }

    /**
     * The latest (by {@code season.startDate}) row sharing {@code teamIdentityId} -- used to resolve
     * an identity-keyed role scope ({@code TEAM_ADMIN}) or nav area back to a concrete row for
     * display (club/name), since those don't care which season's copy they read as long as it's a
     * reasonably current one.
     */
    @Transactional(readOnly = true)
    public Optional<Team> latestForIdentity(String teamIdentityId) {
        return repository.findByTeamIdentityId(teamIdentityId).stream()
            .max(Comparator.comparing(TeamService::startDateOrMin));
    }

    /** {@code LocalDate.MIN} when the team has no season, or its season has no start date set. */
    private static LocalDate startDateOrMin(Team team) {
        Season season = team.getSeason();
        LocalDate startDate = season == null ? null : season.getStartDate();
        return startDate == null ? LocalDate.MIN : startDate;
    }

    /**
     * {@code id} may be either a specific team row's id or a team's stable {@code teamIdentityId}
     * (falls back to the latest season-copy in that case) -- the frontend's nav-area/route only ever
     * carries the identity, since a team's row id changes per season-copy (see
     * {@link #latestForIdentity}).
     */
    @Transactional(readOnly = true)
    public TeamDto get(String id) {
        Team team = repository.findById(id)
            .or(() -> latestForIdentity(id))
            .orElseThrow(() -> new TeamNotFoundException(id));
        return toDtoAsOf(team);
    }

    @Transactional
    public TeamDto create(TeamDto teamDto) {
        // Every team belongs to a club — there are no clubless teams.
        if (teamDto.getClubId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A team requires a club");
        }
        Team newTeam = mapper.toEntity(teamDto);
        newTeam.setSeason(resolveSeason(teamDto.getSeasonId()));
        resolveClub(teamDto, newTeam);
        Team savedTeam = repository.save(newTeam);
        return toDtoAsOf(savedTeam);
    }

    @Transactional
    public TeamDto update(String id, TeamDto teamDto) {
        Team team = repository.findById(id).orElseThrow(
            () -> new TeamNotFoundException(id));

        mapper.updateEntityFromDto(teamDto, team);
        resolveClub(teamDto, team);

        Team savedTeam = repository.save(team);
        return toDtoAsOf(savedTeam);
    }

    @Transactional
    public void delete(String id) {
        Team team = repository.findById(id).orElseThrow(
            () -> new TeamNotFoundException(id));
        repository.delete(team);
    }

    /**
     * Resolves {@code teamId} (any existing row belonging to that team's identity) to the row
     * belonging to {@code targetSeason}, creating a fresh copy (name/club/identity carried over) if
     * this season doesn't have one yet. Used for placement registration (a team returning after a
     * gap year, or a second participation in an already copied-forward season) and by
     * {@code CopyForwardService}, which always hits the create branch since its target season starts
     * empty.
     */
    @Transactional
    public Team resolveForSeason(String teamId, Season targetSeason) {
        Team source = repository.findById(teamId).orElseThrow(() -> new TeamNotFoundException(teamId));
        if (source.getSeason() != null && Objects.equals(source.getSeason().getId(), targetSeason.getId())) {
            return source;
        }
        return repository.findByTeamIdentityIdAndSeason(source.getTeamIdentityId(), targetSeason)
            .orElseGet(() -> {
                Team copy = new Team();
                copy.setName(source.getName());
                copy.setClub(source.getClub()); // Club isn't season-scoped -- carried over verbatim
                copy.setTeamIdentityId(source.getTeamIdentityId());
                copy.setCopiedFromTeamId(source.getId());
                copy.setSeason(targetSeason);
                return repository.save(copy);
            });
    }

    private void resolveClub(TeamDto dto, Team team) {
        if (dto.getClubId() != null) {
            Club club = clubRepository.findById(dto.getClubId())
                .orElseThrow(() -> new ClubNotFoundException(dto.getClubId()));
            team.setClub(club);
        }
    }

    private Season resolveSeason(String seasonId) {
        if (seasonId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A team requires a season");
        }
        return seasonRepository.findById(seasonId)
            .orElseThrow(() -> new SeasonNotFoundException(seasonId));
    }

    private List<TeamDto> toDtoListAsOf(List<Team> teams) {
        return teams.stream().map(this::toDtoAsOf).toList();
    }

    /**
     * Maps a team, resolving its club's display name. For an ENDED season, this freezes the name as
     * it stood at that season's start -- Club stays a single, un-duplicated row (see
     * {@code EntityHistoryService}), so a club rename after a past season must not rewrite that
     * season's historical display. A current/future season is still live, so it always shows today's
     * club name instead -- otherwise a rename would incorrectly freeze the in-progress season at
     * whatever name was current at its start, hiding the update.
     */
    private TeamDto toDtoAsOf(Team team) {
        TeamDto dto = mapper.toDto(team);
        Club club = team.getClub();
        if (club != null) {
            Season season = team.getSeason();
            boolean ended = season != null && season.hasEnded();
            Instant asOf = ended && season.getStartDate() != null
                ? season.getStartDate().atStartOfDay(ZoneOffset.UTC).toInstant() : null;
            String name = asOf == null ? club.getName()
                : historyService.fieldsAsOf(HistoryEntityType.CLUB, club.getId(), List.of("name"), asOf)
                    .getOrDefault("name", club.getName());
            dto.setClubName(name);
        }
        return dto;
    }
}
