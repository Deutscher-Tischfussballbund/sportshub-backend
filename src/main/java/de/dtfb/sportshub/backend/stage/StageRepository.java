package de.dtfb.sportshub.backend.stage;

import de.dtfb.sportshub.backend.discipline.Discipline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StageRepository extends JpaRepository<Stage, String> {
    Optional<Stage> findByDisciplineAndName(Discipline discipline, String name);

    @Query("select e from Stage e where e.discipline.competition.season.archivedAt is null")
    List<Stage> findAllVisible();

    @Query("select e from Stage e where e.id = :id and e.discipline.competition.season.archivedAt is null")
    Optional<Stage> findVisibleById(String id);
}
