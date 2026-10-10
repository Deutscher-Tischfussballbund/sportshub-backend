package de.dtfb.sportshub.backend.maintenance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MaintenanceNoticeRepository extends JpaRepository<MaintenanceNotice, String> {

    List<MaintenanceNotice> findAllByOrderByStartsAtDesc();

    /** A read-only window is running right now. */
    boolean existsByReadOnlyTrueAndStartsAtLessThanEqualAndEndsAtGreaterThan(Instant now, Instant alsoNow);

    /** Notices announced by now whose window hasn't ended, soonest window first. */
    List<MaintenanceNotice> findByAnnounceFromLessThanEqualAndEndsAtGreaterThanOrderByStartsAtAsc(Instant now,
                                                                                                  Instant alsoNow);
}
