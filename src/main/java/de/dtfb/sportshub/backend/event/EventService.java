package de.dtfb.sportshub.backend.event;

import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonNotFoundException;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EventService {
    private final EventRepository repository;
    private final EventMapper mapper;
    private final SeasonRepository seasonRepository;

    public EventService(EventRepository repository, EventMapper mapper, SeasonRepository seasonRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.seasonRepository = seasonRepository;
    }

    @Transactional(readOnly = true)
    public List<EventDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public EventDto get(String id) {
        Event event = repository.findById(id).orElseThrow(
            () -> new EventNotFoundException(id));
        return mapper.toDto(event);
    }

    @Transactional
    public EventDto create(EventDto eventDto) {
        Event event = mapper.toEntity(eventDto);

        Season season = seasonRepository.findById(eventDto.getSeasonId())
            .orElseThrow(() -> new SeasonNotFoundException(eventDto.getSeasonId()));
        event.setSeason(season);

        Event savedEvent = repository.save(event);
        return mapper.toDto(savedEvent);
    }

    @Transactional
    public EventDto update(String id, EventDto eventDto) {
        Event event = repository.findById(id).orElseThrow(
            () -> new EventNotFoundException(id));

        mapper.updateEntityFromDto(eventDto, event);

        Season season = seasonRepository.findById(eventDto.getSeasonId())
            .orElseThrow(() -> new SeasonNotFoundException(eventDto.getSeasonId()));
        event.setSeason(season);

        Event savedEvent = repository.save(event);
        return mapper.toDto(savedEvent);
    }

    @Transactional
    public void delete(String id) {
        Event event = repository.findById(id).orElseThrow(
            () -> new EventNotFoundException(id));
        repository.delete(event);
    }
}
