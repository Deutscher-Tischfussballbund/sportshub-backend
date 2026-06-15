package de.dtfb.sportshub.backend.user;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.federation.Federation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class UserFederationRole extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "federation_id")
    private Federation federation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppRole role;
}