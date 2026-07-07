package de.dtfb.sportshub.backend.tier;

import de.dtfb.sportshub.backend.competition.Competition;
import de.dtfb.sportshub.backend.competition.CompetitionNotFoundException;
import de.dtfb.sportshub.backend.competition.CompetitionRepository;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetNotFoundException;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TierService {
    private final TierRepository repository;
    private final TierMapper mapper;
    private final CompetitionRepository competitionRepository;
    private final LeagueRuleSetRepository ruleSetRepository;

    public TierService(TierRepository repository,
                       TierMapper mapper,
                       CompetitionRepository competitionRepository,
                       LeagueRuleSetRepository ruleSetRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.competitionRepository = competitionRepository;
        this.ruleSetRepository = ruleSetRepository;
    }

    @Transactional(readOnly = true)
    public List<TierDto> getAll() {
        return mapper.toDtoList(repository.findAllVisible());
    }

    @Transactional(readOnly = true)
    public TierDto get(String id) {
        Tier tier = repository.findVisibleById(id).orElseThrow(
            () -> new TierNotFoundException(id));
        return mapper.toDto(tier);
    }

    @Transactional
    public TierDto create(TierDto tierDto) {
        Tier tier = mapper.toEntity(tierDto);
        tier.setCompetition(resolveCompetition(tierDto.getCompetitionId()));
        tier.setRuleSet(resolveRuleSet(tierDto.getRuleSetId()));
        return mapper.toDto(repository.save(tier));
    }

    @Transactional
    public TierDto update(String id, TierDto tierDto) {
        Tier tier = repository.findById(id).orElseThrow(
            () -> new TierNotFoundException(id));
        mapper.updateEntityFromDto(tierDto, tier);
        tier.setCompetition(resolveCompetition(tierDto.getCompetitionId()));
        tier.setRuleSet(resolveRuleSet(tierDto.getRuleSetId()));
        return mapper.toDto(repository.save(tier));
    }

    @Transactional
    public void delete(String id) {
        Tier tier = repository.findById(id).orElseThrow(
            () -> new TierNotFoundException(id));
        repository.delete(tier);
    }

    private Competition resolveCompetition(String competitionId) {
        return competitionRepository.findById(competitionId)
            .orElseThrow(() -> new CompetitionNotFoundException(competitionId));
    }

    private LeagueRuleSet resolveRuleSet(String ruleSetId) {
        if (ruleSetId == null) {
            return null;
        }
        return ruleSetRepository.findById(ruleSetId)
            .orElseThrow(() -> new LeagueRuleSetNotFoundException(ruleSetId));
    }
}
