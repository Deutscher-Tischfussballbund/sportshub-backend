package de.dtfb.sportshub.backend.club;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClubMapper {

    // Frontend vocabulary: a club's "regionId" is its federation (region == federation).
    @Mapping(target = "regionId", source = "federationId")
    ClubDto toDto(Club club);

    List<ClubDto> toDtoList(List<Club> clubs);
}
