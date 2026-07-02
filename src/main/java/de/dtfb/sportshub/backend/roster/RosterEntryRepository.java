package de.dtfb.sportshub.backend.roster;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RosterEntryRepository extends JpaRepository<RosterEntry, String> {

    /** Active roster of a participation (players currently on it). */
    List<RosterEntry> findByParticipationIdAndRemovedAtIsNull(String participationId);

    /** The active entry for a specific player, if present (dup guard + remove target). */
    Optional<RosterEntry> findByParticipationIdAndPlayerIdAndRemovedAtIsNull(String participationId, String playerId);
}
