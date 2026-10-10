package de.dtfb.sportshub.backend.maintenance;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Planned maintenance windows (SPO-119): kept by global admins, shown as a bar on top of the app. */
@Service
public class MaintenanceNoticeService {

    private final MaintenanceNoticeRepository repository;

    public MaintenanceNoticeService(MaintenanceNoticeRepository repository) {
        this.repository = repository;
    }

    /** The notice to show now: announced, window not over yet -- the soonest if several. */
    @Transactional(readOnly = true)
    public Optional<MaintenanceNoticeDto> current() {
        Instant now = Instant.now();
        return repository.findByAnnounceFromLessThanEqualAndEndsAtGreaterThanOrderByStartsAtAsc(now, now).stream()
            .findFirst().map(MaintenanceNoticeDto::of);
    }

    /** A window marked read-only is running: only global admins may change data now. */
    @Transactional(readOnly = true)
    public boolean isReadOnlyNow() {
        Instant now = Instant.now();
        return repository.existsByReadOnlyTrueAndStartsAtLessThanEqualAndEndsAtGreaterThan(now, now);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceNoticeDto> all() {
        return repository.findAllByOrderByStartsAtDesc().stream().map(MaintenanceNoticeDto::of).toList();
    }

    @Transactional
    public MaintenanceNoticeDto create(MaintenanceNoticeDto dto) {
        return MaintenanceNoticeDto.of(repository.save(apply(new MaintenanceNotice(), dto)));
    }

    @Transactional
    public MaintenanceNoticeDto update(String id, MaintenanceNoticeDto dto) {
        MaintenanceNotice notice = repository.findById(id).orElseThrow(() -> new MaintenanceNoticeNotFoundException(id));
        return MaintenanceNoticeDto.of(repository.save(apply(notice, dto)));
    }

    @Transactional
    public void delete(String id) {
        repository.delete(repository.findById(id).orElseThrow(() -> new MaintenanceNoticeNotFoundException(id)));
    }

    /** Both texts required; the window must end after it starts; announced at the latest when it starts. */
    private static MaintenanceNotice apply(MaintenanceNotice notice, MaintenanceNoticeDto dto) {
        if (isBlank(dto.messageDe()) || isBlank(dto.messageEn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messageDe and messageEn are required");
        }
        if (dto.startsAt() == null || dto.endsAt() == null || !dto.endsAt().isAfter(dto.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endsAt must be after startsAt");
        }
        Instant announceFrom = dto.announceFrom() != null ? dto.announceFrom() : dto.startsAt();
        if (announceFrom.isAfter(dto.startsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "announceFrom must not be after startsAt");
        }
        notice.setMessageDe(dto.messageDe().trim());
        notice.setMessageEn(dto.messageEn().trim());
        notice.setStartsAt(dto.startsAt());
        notice.setEndsAt(dto.endsAt());
        notice.setAnnounceFrom(announceFrom);
        notice.setReadOnly(Boolean.TRUE.equals(dto.readOnly()));
        notice.setUpdatedAt(Instant.now());
        return notice;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
