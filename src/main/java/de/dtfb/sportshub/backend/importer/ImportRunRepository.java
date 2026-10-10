package de.dtfb.sportshub.backend.importer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportRunRepository extends JpaRepository<ImportRun, String> {

    List<ImportRun> findAllByOrderByCreatedAtDesc();
}
