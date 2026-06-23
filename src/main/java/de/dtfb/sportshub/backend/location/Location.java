package de.dtfb.sportshub.backend.location;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.federation.Federation;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Location extends BaseEntity {
    private String name;
    private String address;

    /** The region (Landesverband) this venue belongs to; null = a global venue (admin-only). */
    @ManyToOne
    @JoinColumn(name = "federation_id")
    private Federation federation;
}
