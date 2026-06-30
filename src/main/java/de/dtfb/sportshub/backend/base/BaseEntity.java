package de.dtfb.sportshub.backend.base;

import de.dtfb.sportshub.backend.util.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter

public abstract class BaseEntity {
    @Id
    @Column(length = 14, nullable = false, updatable = false)
    private String id;

    @PrePersist
    void generateId() {
        if (id == null) {
            id = IdGenerator.newId();
        }
    }
}
