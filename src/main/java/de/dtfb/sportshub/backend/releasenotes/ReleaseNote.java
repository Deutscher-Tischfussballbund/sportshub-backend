package de.dtfb.sportshub.backend.releasenotes;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class ReleaseNote extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String titleDe;

    @Column(nullable = false, length = 200)
    private String titleEn;

    @Column(nullable = false, length = 4000)
    private String bodyDe;

    @Column(nullable = false, length = 4000)
    private String bodyEn;

    @Column(nullable = false)
    private Instant publishedAt;
}
