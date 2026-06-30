package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.importer.data.ImportMeta;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FederationImporter {
    private final FederationRepository federationRepository;

    public FederationImporter(FederationRepository federationRepository) {
        this.federationRepository = federationRepository;
    }

    @Transactional
    public Federation importFederation(ImportMeta importingMeta) {

        return federationRepository
            .findByName(importingMeta.getOrganisation())
            .orElseGet(() -> {
                Federation fed = new Federation();
                fed.setName(importingMeta.getOrganisation());
                return federationRepository.save(fed);
            });
    }
}
