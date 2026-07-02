package de.dtfb.sportshub.backend.discipline;

import de.dtfb.sportshub.backend.category.Category;
import de.dtfb.sportshub.backend.competition.Competition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DisciplineRepository extends JpaRepository<Discipline, String> {
    Optional<Discipline> findByCompetitionAndCategory(Competition competition, Category category);

    /** Disciplines of one competition (copy-forward source walk). */
    List<Discipline> findByCompetitionId(String competitionId);

    @Query("select e from Discipline e where e.competition.season.archivedAt is null")
    List<Discipline> findAllVisible();

    @Query("select e from Discipline e where e.id = :id and e.competition.season.archivedAt is null")
    Optional<Discipline> findVisibleById(String id);
}
