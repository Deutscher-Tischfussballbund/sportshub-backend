package de.dtfb.sportshub.backend.player;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlayerNumberSequenceRepository extends JpaRepository<PlayerNumberSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PlayerNumberSequence s WHERE s.prefix = :prefix")
    Optional<PlayerNumberSequence> lockByPrefix(@Param("prefix") String prefix);
}
