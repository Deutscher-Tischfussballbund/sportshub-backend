package de.dtfb.sportshub.backend.discipline;

import de.dtfb.sportshub.backend.event.Event;
import de.dtfb.sportshub.backend.event.EventNotFoundException;
import de.dtfb.sportshub.backend.event.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DisciplineService {
    private final DisciplineRepository repository;
    private final DisciplineMapper mapper;
    private final EventRepository eventRepository;

    public DisciplineService(DisciplineRepository repository, DisciplineMapper mapper, EventRepository eventRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventRepository = eventRepository;
    }

    List<DisciplineDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    DisciplineDto get(String uuid) {
        Discipline discipline = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new DisciplineNotFoundException(uuid));
        return mapper.toDto(discipline);
    }

    DisciplineDto create(DisciplineDto disciplineDto) {
        Discipline discipline = mapper.toEntity(disciplineDto);
        discipline.setUuid(UUID.randomUUID());

        Event event = eventRepository.findByUuid(disciplineDto.getEventUuid())
            .orElseThrow(() -> new EventNotFoundException(disciplineDto.getEventUuid().toString()));
        discipline.setEvent(event);

        Discipline savedDiscipline = repository.save(discipline);
        return mapper.toDto(savedDiscipline);
    }

    DisciplineDto update(String uuid, DisciplineDto disciplineDto) {
        Discipline discipline = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new DisciplineNotFoundException(uuid));

        mapper.updateEntityFromDto(disciplineDto, discipline);

        Event event = eventRepository.findByUuid(disciplineDto.getEventUuid())
            .orElseThrow(() -> new EventNotFoundException(disciplineDto.getEventUuid().toString()));
        discipline.setEvent(event);

        Discipline savedDiscipline = repository.save(discipline);
        return mapper.toDto(savedDiscipline);
    }

    @Transactional
    void delete(String uuid) {
        Discipline discipline = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new DisciplineNotFoundException(uuid));
        repository.delete(discipline);
    }
}
