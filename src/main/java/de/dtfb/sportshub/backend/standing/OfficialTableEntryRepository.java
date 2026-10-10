package de.dtfb.sportshub.backend.standing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfficialTableEntryRepository extends JpaRepository<OfficialTableEntry, String> {

    List<OfficialTableEntry> findByGroupId(String groupId);
}
