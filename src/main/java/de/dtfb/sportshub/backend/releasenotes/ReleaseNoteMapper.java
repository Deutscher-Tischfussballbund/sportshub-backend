package de.dtfb.sportshub.backend.releasenotes;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReleaseNoteMapper {

    ReleaseNoteDto toDto(ReleaseNote releaseNote);

    // publishedAt is server-controlled (ReleaseNoteService sets it on create, preserves it on
    // update) -- ignored here so a client-supplied value in the DTO can never override it.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    ReleaseNote toEntity(ReleaseNoteDto releaseNoteDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    void updateEntityFromDto(ReleaseNoteDto dto, @MappingTarget ReleaseNote entity);

    List<ReleaseNoteDto> toDtoList(List<ReleaseNote> releaseNotes);
}
