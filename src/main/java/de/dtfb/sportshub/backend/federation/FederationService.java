package de.dtfb.sportshub.backend.federation;

import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetNotFoundException;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FederationService {
    private final FederationRepository repository;
    private final FederationMapper mapper;
    private final LeagueRuleSetRepository ruleSetRepository;

    public FederationService(FederationRepository repository,
                             FederationMapper mapper,
                             LeagueRuleSetRepository ruleSetRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.ruleSetRepository = ruleSetRepository;
    }

    @Transactional(readOnly = true)
    public List<FederationDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public FederationDto get(String id) {
        Federation federation = repository.findById(id).orElseThrow(
            () -> new FederationNotFoundException(id));
        return mapper.toDto(federation);
    }

    @Transactional
    public FederationDto create(FederationDto federationDto) {
        Federation federation = mapper.toEntity(federationDto);
        federation.setDefaultRuleSet(resolveRuleSet(federationDto.getDefaultRuleSetId()));

        Federation savedFederation = repository.save(federation);
        return mapper.toDto(savedFederation);
    }

    @Transactional
    public FederationDto update(String id, FederationDto federationDto) {
        Federation federation = repository.findById(id).orElseThrow(
            () -> new FederationNotFoundException(id));

        mapper.updateEntityFromDto(federationDto, federation);
        federation.setDefaultRuleSet(resolveRuleSet(federationDto.getDefaultRuleSetId()));

        Federation savedFederation = repository.save(federation);
        return mapper.toDto(savedFederation);
    }

    private LeagueRuleSet resolveRuleSet(String ruleSetId) {
        if (ruleSetId == null) {
            return null;
        }
        return ruleSetRepository.findById(ruleSetId)
            .orElseThrow(() -> new LeagueRuleSetNotFoundException(ruleSetId));
    }

    @Transactional
    public void delete(String id) {
        Federation federation = repository.findById(id).orElseThrow(
            () -> new FederationNotFoundException(id));
        repository.delete(federation);
    }
}
