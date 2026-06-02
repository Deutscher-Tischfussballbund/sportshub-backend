package de.dtfb.sportshub.backend.discipline;

import de.dtfb.sportshub.backend.category.Category;
import de.dtfb.sportshub.backend.category.CategoryNotFoundException;
import de.dtfb.sportshub.backend.category.CategoryRepository;
import de.dtfb.sportshub.backend.event.Event;
import de.dtfb.sportshub.backend.event.EventNotFoundException;
import de.dtfb.sportshub.backend.event.EventRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DisciplineService {
    private final DisciplineRepository repository;
    private final DisciplineMapper mapper;
    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;

    public DisciplineService(DisciplineRepository repository, DisciplineMapper mapper, EventRepository eventRepository, CategoryRepository categoryRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
    }

    List<DisciplineDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    DisciplineDto get(String uuid) {
        return mapper.toDto(getDiscipline(uuid));
    }

    @Transactional
    DisciplineDto create(DisciplineDto disciplineDto) {
        Discipline discipline = mapper.toEntity(disciplineDto);

        discipline.setEvent(getEvent(disciplineDto));
        discipline.setCategory(getCategory(disciplineDto));

        return mapper.toDto(repository.save(discipline));
    }

    @Transactional
    DisciplineDto update(String uuid, DisciplineDto disciplineDto) {
        Discipline discipline = getDiscipline(uuid);

        mapper.updateEntityFromDto(disciplineDto, discipline);

        discipline.setEvent(getEvent(disciplineDto));
        discipline.setCategory(getCategory(disciplineDto));

        return mapper.toDto(repository.save(discipline));
    }

    @Transactional
    void delete(String uuid) {
        Discipline discipline = getDiscipline(uuid);
        repository.delete(discipline);
    }

    private @NonNull Discipline getDiscipline(String uuid) {
        return repository.findById(uuid).orElseThrow(
            () -> new DisciplineNotFoundException(uuid));
    }

    private @NonNull Category getCategory(DisciplineDto disciplineDto) {
        return categoryRepository.findById(disciplineDto.getEventId())
            .orElseThrow(() -> new CategoryNotFoundException(disciplineDto.getCategoryId()));
    }

    private @NonNull Event getEvent(DisciplineDto disciplineDto) {
        return eventRepository.findById(disciplineDto.getEventId())
            .orElseThrow(() -> new EventNotFoundException(disciplineDto.getEventId()));
    }
}
