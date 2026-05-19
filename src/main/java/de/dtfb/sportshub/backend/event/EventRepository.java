package de.dtfb.sportshub.backend.event;

import de.dtfb.sportshub.backend.season.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, String> {
    Optional<Event> findBySeasonAndName(Season season, String name);
}
