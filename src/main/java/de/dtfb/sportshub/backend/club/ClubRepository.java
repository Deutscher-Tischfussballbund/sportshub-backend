package de.dtfb.sportshub.backend.club;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClubRepository extends JpaRepository<Club, String> {

    List<Club> findByFederationId(String federationId);

    /** Clubs narrowed by the optional filters (SPO-120); {@code q} searches name, short name and town. */
    @Query("""
        select c from Club c where (:federationId is null or c.federationId = :federationId)
          and (:active is null or c.active = :active)
          and (:q is null or lower(c.name) like :q or lower(c.shortName) like :q or lower(c.city) like :q)""")
    List<Club> findFiltered(@Param("federationId") String federationId, @Param("active") Boolean active,
                            @Param("q") String q);
}
