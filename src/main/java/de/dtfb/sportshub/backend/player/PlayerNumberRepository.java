package de.dtfb.sportshub.backend.player;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerNumberRepository extends JpaRepository<PlayerNumber, String> {

    /** A current or old number -- numbers are never reused, so at most one row. */
    Optional<PlayerNumber> findByNumber(String number);

    Optional<PlayerNumber> findByPlayerIdAndValidToIsNull(String playerId);

    List<PlayerNumber> findByPlayerIdOrderByValidFromAsc(String playerId);

    boolean existsByPlayerId(String playerId);
}
