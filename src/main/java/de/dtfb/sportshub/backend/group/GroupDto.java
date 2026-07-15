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
}
