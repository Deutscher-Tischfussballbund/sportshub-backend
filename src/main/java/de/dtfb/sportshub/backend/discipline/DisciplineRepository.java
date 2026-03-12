package de.dtfb.sportshub.backend.discipline;

import de.dtfb.sportshub.backend.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DisciplineRepository extends JpaRepository<Discipline, UUID> {
    Optional<Discipline> findByEventAndName(Event event, String name);
}
