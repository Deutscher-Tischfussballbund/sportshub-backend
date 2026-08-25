package de.dtfb.sportshub.backend.roster;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RosterEntryMapper {

    @Mapping(source = "participation.id", target = "participationId")
    @Mapping(source = "player.id", target = "playerId")
    @Mapping(target = "firstName", ignore = true) // set by RosterService, resolved as of addedAt
    @Mapping(target = "lastName", ignore = true)
    RosterEntryDto toDto(RosterEntry entry);

    List<RosterEntryDto> toDtoList(List<RosterEntry> entries);
}
