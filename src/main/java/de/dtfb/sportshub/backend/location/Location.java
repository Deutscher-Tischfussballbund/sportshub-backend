package de.dtfb.sportshub.backend.location;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Location extends BaseEntity {
    private String name;
    private String address;
}
