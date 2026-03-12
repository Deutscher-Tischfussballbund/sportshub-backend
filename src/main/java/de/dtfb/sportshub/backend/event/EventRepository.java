package de.dtfb.sportshub.backend.event;

import de.dtfb.sportshub.backend.season.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findBySeasonAndName(Season season, String name);
}
