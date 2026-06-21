package de.dtfb.sportshub.backend.club;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A Verein, belonging to a Federation (Landesverband). Nano-id keyed via {@link BaseEntity}. */
@Entity
@Table(name = "club")
@Getter
@Setter
public class Club extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String shortName;
    private String city;

    @Column(nullable = false)
    private boolean active = true;

    /** References {@code federation.Federation#getId()}. */
    @Column(nullable = false)
    private String federationId;
}
