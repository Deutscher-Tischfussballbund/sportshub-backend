package de.dtfb.sportshub.backend.discipline;

import de.dtfb.sportshub.backend.category.Category;
import de.dtfb.sportshub.backend.competition.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DisciplineRepository extends JpaRepository<Discipline, String> {
    Optional<Discipline> findByCompetitionAndCategory(Competition competition, Category category);
}
