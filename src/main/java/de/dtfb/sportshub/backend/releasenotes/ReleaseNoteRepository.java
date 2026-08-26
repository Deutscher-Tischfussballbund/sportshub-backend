package de.dtfb.sportshub.backend.releasenotes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReleaseNoteRepository extends JpaRepository<ReleaseNote, String> {
    List<ReleaseNote> findAllByOrderByPublishedAtDesc();
}
