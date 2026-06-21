package de.dtfb.sportshub.backend.player;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, String> {

    Optional<Player> findByDtfbId(String dtfbId);

    List<Player> findByDtfbIdIn(Collection<String> dtfbIds);

    List<Player> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrNationalIdContainingIgnoreCase(
        String firstName, String lastName, String nationalId);
}
