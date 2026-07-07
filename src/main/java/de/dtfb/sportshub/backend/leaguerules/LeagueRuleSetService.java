package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeagueRuleSetService {
    private final LeagueRuleSetRepository repository;
    private final GamePlanEntryRepository gamePlanRepository;
    private final LeagueRuleSetMapper mapper;
    private final FederationRepository federationRepository;

    public LeagueRuleSetService(LeagueRuleSetRepository repository,
                                GamePlanEntryRepository gamePlanRepository,
                                LeagueRuleSetMapper mapper,
                                FederationRepository federationRepository) {
        this.repository = repository;
        this.gamePlanRepository = gamePlanRepository;
        this.mapper = mapper;
        this.federationRepository = federationRepository;
    }

    @Transactional(readOnly = true)
    public List<LeagueRuleSetDto> getAll() {
        return repository.findAll().stream().map(this::assemble).toList();
    }

    @Transactional(readOnly = true)
    public LeagueRuleSetDto get(String id) {
        LeagueRuleSet ruleSet = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        return assemble(ruleSet);
    }

    @Transactional
    public LeagueRuleSetDto create(LeagueRuleSetDto dto) {
        LeagueRuleSet ruleSet = mapper.toEntity(dto);
        ruleSet.setFederation(resolveFederation(dto.getFederationId()));
        LeagueRuleSet saved = repository.save(ruleSet);
        replaceGamePlan(saved, dto.getGamePlan());
        return assemble(saved);
    }

    @Transactional
    public LeagueRuleSetDto update(String id, LeagueRuleSetDto dto) {
        LeagueRuleSet ruleSet = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        mapper.updateEntityFromDto(dto, ruleSet);
        ruleSet.setFederation(resolveFederation(dto.getFederationId()));
        LeagueRuleSet saved = repository.save(ruleSet);
        replaceGamePlan(saved, dto.getGamePlan());
        return assemble(saved);
    }

    @Transactional
    public void delete(String id) {
        LeagueRuleSet ruleSet = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        gamePlanRepository.deleteByRuleSetId(id);
        repository.delete(ruleSet);
    }

    private Federation resolveFederation(String federationId) {
        if (federationId == null) {
            return null; // DTFB-global template
        }
        return federationRepository.findById(federationId)
            .orElseThrow(() -> new FederationNotFoundException(federationId));
    }

    private void replaceGamePlan(LeagueRuleSet ruleSet, List<GamePlanEntryDto> gamePlan) {
        gamePlanRepository.deleteByRuleSetId(ruleSet.getId());
        if (gamePlan == null) {
            return;
        }
        for (GamePlanEntryDto entryDto : gamePlan) {
            GamePlanEntry entry = new GamePlanEntry();
            entry.setRuleSet(ruleSet);
            entry.setPosition(entryDto.getPosition());
            entry.setGameType(entryDto.getGameType());
            gamePlanRepository.save(entry);
        }
    }

    private LeagueRuleSetDto assemble(LeagueRuleSet ruleSet) {
        LeagueRuleSetDto dto = mapper.toDto(ruleSet);
        dto.setGamePlan(mapper.toGamePlanDtoList(
            gamePlanRepository.findByRuleSetIdOrderByPositionAsc(ruleSet.getId())));
        return dto;
    }
}
