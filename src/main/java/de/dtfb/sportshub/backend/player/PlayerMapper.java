package de.dtfb.sportshub.backend.player;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlayerMapper {

    // Player↔club membership is not modelled yet → empty list rather than null.
    @Mapping(target = "clubs", expression = "java(java.util.List.of())")
    PlayerDto toDto(Player player);

    List<PlayerDto> toDtoList(List<Player> players);
}
