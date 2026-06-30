package de.dtfb.sportshub.backend.club;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClubRepository extends JpaRepository<Club, String> {

    List<Club> findByFederationId(String federationId);
}
