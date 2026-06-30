package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.pool.Pool;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Round extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "pool_id")
    private Pool pool;

    private String name;
    private Integer index;
}
