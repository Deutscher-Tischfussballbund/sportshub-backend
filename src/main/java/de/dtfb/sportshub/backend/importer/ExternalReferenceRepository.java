package de.dtfb.sportshub.backend.importer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalReferenceRepository extends JpaRepository<ExternalReference, String> {

    List<ExternalReference> findBySourceAndInstance(String source, String instance);
}
