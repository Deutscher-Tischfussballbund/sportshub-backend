package de.dtfb.sportshub.backend.pool;

import de.dtfb.sportshub.backend.stage.Stage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PoolRepository extends JpaRepository<Pool, String> {
    Optional<Pool> findByStageAndName(Stage stage, String name);
}
