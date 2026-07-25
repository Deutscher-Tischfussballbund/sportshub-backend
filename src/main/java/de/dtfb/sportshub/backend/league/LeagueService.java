package de.dtfb.sportshub.backend.league;

import de.dtfb.sportshub.backend.category.Category;
import de.dtfb.sportshub.backend.category.CategoryNotFoundException;
import de.dtfb.sportshub.backend.category.CategoryRepository;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetNotFoundException;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonNotFoundException;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeagueService {
    private final LeagueRepository repository;
    private final LeagueMapper mapper;
    private final SeasonRepository seasonRepository;
    private final CategoryRepository categoryRepository;
    private final LeagueRuleSetRepository ruleSetRepository;
    private final TierRepository tierRepository;
    private final TeamParticipationRepository teamParticipationRepository;

    public LeagueService(LeagueRepository repository, LeagueMapper mapper, SeasonRepository seasonRepository,
                         CategoryRepository categoryRepository, LeagueRuleSetRepository ruleSetRepository,
                         TierRepository tierRepository, TeamParticipationRepository teamParticipationRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.seasonRepository = seasonRepository;
        this.categoryRepository = categoryRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.tierRepository = tierRepository;
        this.teamParticipationRepository = teamParticipationRepository;
    }

    @Transactional(readOnly = true)
    public List<LeagueDto> getAll() {
        // Hide leagues whose season is archived (gateway into the archived subtree).
        return mapper.toDtoList(repository.findBySeason_ArchivedAtIsNull());
    }

    @Transactional(readOnly = true)
    public LeagueDto get(String id) {
        League league = repository.findById(id)
            .filter(l -> l.getSeason() == null || l.getSeason().getArchivedAt() == null)
            .orElseThrow(() -> new LeagueNotFoundException(id));
        return mapper.toDto(league);
    }

    @Transactional
    public LeagueDto create(LeagueDto leagueDto) {
        League league = mapper.toEntity(leagueDto);
        applyRelations(leagueDto, league);
        return mapper.toDto(repository.save(league));
    }

    @Transactional
    public LeagueDto update(String id, LeagueDto leagueDto) {
        League league = repository.findById(id).orElseThrow(
            () -> new LeagueNotFoundException(id));
        mapper.updateEntityFromDto(leagueDto, league);
        applyRelations(leagueDto, league);
        return mapper.toDto(repository.save(league));
    }

    /**
     * A league blocks its own delete while it still has a {@code Tier} or a {@code TeamParticipation}
     * registered directly against it -- a groupless league's participations reference it with no tier
     * at all. Delete the structure/participations first, bottom-up.
     */
    @Transactional
    public void delete(String id) {
        League league = repository.findById(id).orElseThrow(
            () -> new LeagueNotFoundException(id));
        if (tierRepository.existsByLeagueId(id) || teamParticipationRepository.existsByLeague_Id(id)) {
            throw new LeagueDeletionBlockedException(
                "League has tiers or team participations; remove them before deleting the league");
        }
        repository.delete(league);
    }

    private void applyRelations(LeagueDto dto, League league) {
        Season season = seasonRepository.findById(dto.getSeasonId())
            .orElseThrow(() -> new SeasonNotFoundException(dto.getSeasonId()));
        league.setSeason(season);

        Category category = categoryRepository.findById(dto.getCategoryId())
            .orElseThrow(() -> new CategoryNotFoundException(dto.getCategoryId()));
        league.setCategory(category);

        league.setRuleSet(resolveRuleSet(dto.getRuleSetId()));
    }

    private LeagueRuleSet resolveRuleSet(String ruleSetId) {
        if (ruleSetId == null) {
            return null;
        }
        return ruleSetRepository.findById(ruleSetId)
            .orElseThrow(() -> new LeagueRuleSetNotFoundException(ruleSetId));
    }
}
