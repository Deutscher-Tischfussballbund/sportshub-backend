package de.dtfb.sportshub.backend.category;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Category extends BaseEntity {
    private String name;
    private String shortName;
}
