package de.dtfb.sportshub.backend.player;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlayerMapper {

    // clubs is set by the caller afterwards (ClubMembershipService.clubsByPlayerId), not mapped
    // from the entity -- see PlayerDirectoryService/PlayerService.
    @Mapping(target = "clubs", ignore = true)
    PlayerDto toDto(Player player);

    List<PlayerDto> toDtoList(List<Player> players);
}
