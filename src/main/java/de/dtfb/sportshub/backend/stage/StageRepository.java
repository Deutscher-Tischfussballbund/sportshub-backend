package de.dtfb.sportshub.backend.stage;

import de.dtfb.sportshub.backend.discipline.Discipline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StageRepository extends JpaRepository<Stage, UUID> {
    Optional<Stage> findByDisciplineAndName(Discipline discipline, String name);
}
