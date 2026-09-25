package de.dtfb.sportshub.backend.category;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.player.LeagueSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/**
 * A competition class a league runs under (Herren, Damen, Open, ...). Besides being a label, a
 * category is an eligibility profile: each nullable criterion restricts which players may be
 * rostered onto a team in one of its leagues, checked by {@link CategoryEligibility}. See
 * docs/19-category-eligibility.md.
 */
@Entity
@Getter
@Setter
public class Category extends BaseEntity {
    @Column(nullable = false)
    private String name;

    /** Unique (case-insensitive, enforced in {@link CategoryService}; DB unique key since V11). */
    @Column(nullable = false, unique = true)
    private String shortName;

    /** The league side players must compete on; null = open to every gender. */
    @Enumerated(EnumType.STRING)
    private LeagueSide eligibleSide;
}
