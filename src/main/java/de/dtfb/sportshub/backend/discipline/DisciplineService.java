package de.dtfb.sportshub.backend.discipline;

import de.dtfb.sportshub.backend.category.Category;
import de.dtfb.sportshub.backend.category.CategoryNotFoundException;
import de.dtfb.sportshub.backend.category.CategoryRepository;
import de.dtfb.sportshub.backend.competition.Competition;
import de.dtfb.sportshub.backend.competition.CompetitionNotFoundException;
import de.dtfb.sportshub.backend.competition.CompetitionRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DisciplineService {
    private final DisciplineRepository repository;
    private final DisciplineMapper mapper;
    private final CompetitionRepository competitionRepository;
    private final CategoryRepository categoryRepository;

    public DisciplineService(DisciplineRepository repository, DisciplineMapper mapper, CompetitionRepository competitionRepository, CategoryRepository categoryRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.competitionRepository = competitionRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<DisciplineDto> getAll() {
        return mapper.toDtoList(repository.findAllVisible());
    }

    @Transactional(readOnly = true)
    public DisciplineDto get(String id) {
        return mapper.toDto(repository.findVisibleById(id).orElseThrow(
            () -> new DisciplineNotFoundException(id)));
    }

    @Transactional
    public DisciplineDto create(DisciplineDto disciplineDto) {
        Discipline discipline = mapper.toEntity(disciplineDto);

        discipline.setCompetition(getCompetition(disciplineDto));
        discipline.setCategory(getCategory(disciplineDto));

        return mapper.toDto(repository.save(discipline));
    }

    @Transactional
    public DisciplineDto update(String id, DisciplineDto disciplineDto) {
        Discipline discipline = getDiscipline(id);

        mapper.updateEntityFromDto(disciplineDto, discipline);

        discipline.setCompetition(getCompetition(disciplineDto));
        discipline.setCategory(getCategory(disciplineDto));

        return mapper.toDto(repository.save(discipline));
    }

    @Transactional
    public void delete(String id) {
        Discipline discipline = getDiscipline(id);
        repository.delete(discipline);
    }

    private @NonNull Discipline getDiscipline(String id) {
        return repository.findById(id).orElseThrow(
            () -> new DisciplineNotFoundException(id));
    }

    private @NonNull Category getCategory(DisciplineDto disciplineDto) {
        return categoryRepository.findById(disciplineDto.getCategoryId())
            .orElseThrow(() -> new CategoryNotFoundException(disciplineDto.getCategoryId()));
    }

    private @NonNull Competition getCompetition(DisciplineDto disciplineDto) {
        return competitionRepository.findById(disciplineDto.getCompetitionId())
            .orElseThrow(() -> new CompetitionNotFoundException(disciplineDto.getCompetitionId()));
    }
}
