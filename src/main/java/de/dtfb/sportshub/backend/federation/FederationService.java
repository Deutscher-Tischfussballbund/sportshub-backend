package de.dtfb.sportshub.backend.federation;

import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetNotFoundException;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import de.dtfb.sportshub.backend.round.RoundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class FederationService {
    private final FederationRepository repository;
    private final FederationMapper mapper;
    private final LeagueRuleSetRepository ruleSetRepository;
    private final RoundRepository roundRepository;

    public FederationService(FederationRepository repository,
                             FederationMapper mapper,
                             LeagueRuleSetRepository ruleSetRepository,
                             RoundRepository roundRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.ruleSetRepository = ruleSetRepository;
        this.roundRepository = roundRepository;
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

        String currentRuleSetId = federation.getDefaultRuleSet() == null
            ? null : federation.getDefaultRuleSet().getId();
        String newRuleSetId = federationDto.getDefaultRuleSetId();
        if (!Objects.equals(currentRuleSetId, newRuleSetId)
            && roundRepository.existsFixtureForFederationDefaultDependentTier(id)) {
            throw new FederationDefaultRuleSetChangeBlockedException(
                "A tier in this federation already has fixtures and relies on the current default "
                    + "rule set; give it an explicit rule set before changing the default");
        }

        mapper.updateEntityFromDto(federationDto, federation);
        federation.setDefaultRuleSet(resolveRuleSet(newRuleSetId));

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
