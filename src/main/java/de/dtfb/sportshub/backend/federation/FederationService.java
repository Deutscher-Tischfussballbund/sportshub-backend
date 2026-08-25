package de.dtfb.sportshub.backend.federation;

import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetNotFoundException;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import de.dtfb.sportshub.backend.round.RoundRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        federation.setParentFederation(resolveParentForCreate(federationDto.getParentFederationId()));

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

        // A null parentFederationId here means "unchanged" (most callers save the whole object back
        // without touching this field) -- re-parenting/re-rooting a federation is an explicit,
        // deliberate move, not something an omitted field should trigger.
        if (federationDto.getParentFederationId() != null) {
            Federation newParent = repository.findById(federationDto.getParentFederationId())
                .orElseThrow(() -> new FederationNotFoundException(federationDto.getParentFederationId()));
            requireNoCycle(federation, newParent);
            federation.setParentFederation(newParent);
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

    /**
     * A new federation with no explicit parent attaches under the existing root, if any -- callers
     * shouldn't need to know/pass the root's id just to create an ordinary sub-federation. The very
     * first federation ever created (no root exists yet) becomes the root itself. This keeps the
     * tree singly-rooted without a separate "no other root" guard.
     */
    private Federation resolveParentForCreate(String parentFederationId) {
        if (parentFederationId != null) {
            return repository.findById(parentFederationId)
                .orElseThrow(() -> new FederationNotFoundException(parentFederationId));
        }
        List<Federation> roots = repository.findByParentFederationIsNull();
        return roots.isEmpty() ? null : roots.get(0);
    }

    /** A federation can't become its own ancestor by reassigning its parent. */
    private void requireNoCycle(Federation federation, Federation newParent) {
        for (Federation current = newParent; current != null; current = current.getParentFederation()) {
            if (current.getId().equals(federation.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A federation cannot be its own ancestor");
            }
        }
    }

    @Transactional
    public void delete(String id) {
        Federation federation = repository.findById(id).orElseThrow(
            () -> new FederationNotFoundException(id));
        repository.delete(federation);
    }
}
