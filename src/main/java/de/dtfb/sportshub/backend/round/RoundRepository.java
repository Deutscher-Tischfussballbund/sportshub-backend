package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, String> {
    Optional<Round> findByGroupAndName(Group group, String name);

    @Query("select e from Round e where e.group.tier.league.season.archivedAt is null")
    List<Round> findAllVisible();

    @Query("select e from Round e where e.id = :id and e.group.tier.league.season.archivedAt is null")
    Optional<Round> findVisibleById(String id);
}
