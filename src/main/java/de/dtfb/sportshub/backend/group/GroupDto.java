package de.dtfb.sportshub.backend.group;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupDto {
    private String id;
    private String name;
    private String tierId;

    @NotNull
    private GroupState groupState;

    /** {@code ROUND_ROBIN} (default) or {@code KNOCKOUT} (docs/34). Fixed once the group has rounds. */
    private GroupKind kind;
    /**
     * The group's own rules override: a snapshot private to this group (docs/21), or null = the tier's or
     * league's rules apply. Same contract as {@code TierDto.ruleSetId}: on update, null together with a
     * null {@link #blueprintId} removes an existing override; echoing the current id keeps it.
     */
    private String ruleSetId;
    /** Read-only: the override snapshot's name. */
    private String ruleSetName;
    /** The blueprint of the override; as {@code TierDto.blueprintId}. */
    private String blueprintId;
}
