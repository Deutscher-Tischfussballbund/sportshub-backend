package de.dtfb.sportshub.backend.location;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, String> {

    /** A region's own venues plus the global ones (no region) -- what its pickers offer. */
    @Query("select l from Location l where l.federation.id = :federationId or l.federation is null order by l.name")
    List<Location> findForRegion(@Param("federationId") String federationId);
}
