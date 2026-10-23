package de.dtfb.sportshub.backend.knockout;

import de.dtfb.sportshub.backend.group.GroupDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** The created knock-out group, plus warnings such as {@code DRAW_POSSIBLE} (its rules allow a level result). */
@Getter
@AllArgsConstructor
public class KnockoutStageDto {
    private GroupDto group;
    private List<String> warnings;
}
