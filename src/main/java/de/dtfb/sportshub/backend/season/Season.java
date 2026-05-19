package de.dtfb.sportshub.backend.season;

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
public class Season extends BaseEntity {
    private String name;

    @ManyToOne
    @JoinColumn(name = "federation_id")
    private Federation federation;
}
