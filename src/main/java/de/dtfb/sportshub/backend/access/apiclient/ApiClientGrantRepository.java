package de.dtfb.sportshub.backend.access.apiclient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiClientGrantRepository extends JpaRepository<ApiClientGrant, String> {
    Optional<ApiClientGrant> findByClientId(String clientId);
}
