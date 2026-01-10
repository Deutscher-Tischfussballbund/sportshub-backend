package de.dtfb.sportshub.backend.event;

import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonNotFoundException;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    List<EventDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    EventDto get(String uuid) {
        Event event = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new EventNotFoundException(uuid));
        return mapper.toDto(event);
    }

    EventDto create(EventDto eventDto) {
        Event event = mapper.toEntity(eventDto);

        Season season = seasonRepository.findById(eventDto.getSeasonId())
            .orElseThrow(() -> new SeasonNotFoundException(eventDto.getSeasonId().toString()));
        event.setSeason(season);

        Event savedEvent = repository.save(event);
        return mapper.toDto(savedEvent);
    }

    EventDto update(String uuid, EventDto eventDto) {
        Event event = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new EventNotFoundException(uuid));

        mapper.updateEntityFromDto(eventDto, event);

        Season season = seasonRepository.findById(eventDto.getSeasonId())
            .orElseThrow(() -> new SeasonNotFoundException(eventDto.getSeasonId().toString()));
        event.setSeason(season);

        Event savedEvent = repository.save(event);
        return mapper.toDto(savedEvent);
    }

    @Transactional
    void delete(String uuid) {
        Event event = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new EventNotFoundException(uuid));
        repository.delete(event);
    }
}
