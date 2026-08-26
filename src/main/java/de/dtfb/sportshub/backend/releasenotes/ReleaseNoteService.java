package de.dtfb.sportshub.backend.releasenotes;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ReleaseNoteService {
    private final ReleaseNoteRepository repository;
    private final ReleaseNoteMapper mapper;

    public ReleaseNoteService(ReleaseNoteRepository repository, ReleaseNoteMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ReleaseNoteDto> getAll() {
        return mapper.toDtoList(repository.findAllByOrderByPublishedAtDesc());
    }

    @Transactional
    public ReleaseNoteDto create(ReleaseNoteDto releaseNoteDto) {
        ReleaseNote releaseNote = mapper.toEntity(releaseNoteDto);
        releaseNote.setPublishedAt(Instant.now());

        return mapper.toDto(repository.save(releaseNote));
    }

    @Transactional
    public ReleaseNoteDto update(String id, ReleaseNoteDto releaseNoteDto) {
        ReleaseNote releaseNote = getReleaseNote(id);

        mapper.updateEntityFromDto(releaseNoteDto, releaseNote);

        return mapper.toDto(repository.save(releaseNote));
    }

    @Transactional
    public void delete(String id) {
        repository.delete(getReleaseNote(id));
    }

    private @NonNull ReleaseNote getReleaseNote(String id) {
        return repository.findById(id).orElseThrow(
            () -> new ReleaseNoteNotFoundException(id));
    }
}
