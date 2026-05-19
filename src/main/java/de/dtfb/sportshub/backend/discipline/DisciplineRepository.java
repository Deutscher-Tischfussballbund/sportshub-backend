package de.dtfb.sportshub.backend.discipline;

import de.dtfb.sportshub.backend.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DisciplineRepository extends JpaRepository<Discipline, String> {
    Optional<Discipline> findByName(String name);

    Optional<Discipline> findByEventAndName(Event event, String name);
}
