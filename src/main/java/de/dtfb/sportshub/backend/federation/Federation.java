package de.dtfb.sportshub.backend.federation;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Federation extends BaseEntity {
    private String name;
}
