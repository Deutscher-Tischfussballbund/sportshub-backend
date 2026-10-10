package de.dtfb.sportshub.backend.maintenance;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A planned maintenance window, announced to every logged-in user as a bar on top of the app (SPO-119)
 * from {@link #announceFrom} until {@link #endsAt}. Set by global admins; bilingual like release notes.
 */
@Entity
@Table(name = "maintenance_notice")
@Getter
@Setter
public class MaintenanceNotice extends BaseEntity {

    @Column(nullable = false, length = 500)
    private String messageDe;

    @Column(nullable = false, length = 500)
    private String messageEn;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    /** From when the bar is shown; at the latest when the window starts. */
    @Column(nullable = false)
    private Instant announceFrom;

    /** During the window, only global admins may change data; everyone else reads only. */
    @Column(nullable = false)
    private boolean readOnly;

    /** Changes with every edit -- a user who closed the bar sees it again after a change. */
    @Column(nullable = false)
    private Instant updatedAt;
}
