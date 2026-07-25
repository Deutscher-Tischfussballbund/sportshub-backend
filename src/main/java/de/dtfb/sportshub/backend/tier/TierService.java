package de.dtfb.sportshub.backend.tier;

import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueNotFoundException;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetNotFoundException;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import de.dtfb.sportshub.backend.group.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TierService {
    private final TierRepository repository;
    private final TierMapper mapper;
    private final LeagueRepository leagueRepository;
    private final LeagueRuleSetRepository ruleSetRepository;
    private final GroupRepository groupRepository;

    public TierService(TierRepository repository,
                       TierMapper mapper,
                       LeagueRepository leagueRepository,
                       LeagueRuleSetRepository ruleSetRepository,
                       GroupRepository groupRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.leagueRepository = leagueRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.groupRepository = groupRepository;
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
        tier.setLeague(resolveLeague(tierDto.getLeagueId()));
        tier.setRuleSet(resolveRuleSet(tierDto.getRuleSetId()));
        return mapper.toDto(repository.save(tier));
    }

    @Transactional
    public TierDto update(String id, TierDto tierDto) {
        Tier tier = repository.findById(id).orElseThrow(
            () -> new TierNotFoundException(id));
        mapper.updateEntityFromDto(tierDto, tier);
        tier.setLeague(resolveLeague(tierDto.getLeagueId()));
        tier.setRuleSet(resolveRuleSet(tierDto.getRuleSetId()));
        return mapper.toDto(repository.save(tier));
    }

    /** A tier blocks its own delete while it still has a {@code Group} underneath it. */
    @Transactional
    public void delete(String id) {
        Tier tier = repository.findById(id).orElseThrow(
            () -> new TierNotFoundException(id));
        if (groupRepository.existsByTierId(id)) {
            throw new TierDeletionBlockedException("Tier has groups; remove them before deleting the tier");
        }
        repository.delete(tier);
    }

    private League resolveLeague(String leagueId) {
        return leagueRepository.findById(leagueId)
            .orElseThrow(() -> new LeagueNotFoundException(leagueId));
    }

    private LeagueRuleSet resolveRuleSet(String ruleSetId) {
        if (ruleSetId == null) {
            return null;
        }
        return ruleSetRepository.findById(ruleSetId)
            .orElseThrow(() -> new LeagueRuleSetNotFoundException(ruleSetId));
    }
}
