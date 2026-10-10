package de.dtfb.sportshub.backend.clubmembership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClubMembershipRepository extends JpaRepository<ClubMembership, String> {

    Optional<ClubMembership> findByPlayerIdAndClubIdAndLeftAtIsNull(String playerId, String clubId);

    boolean existsByPlayerIdAndClubIdAndLeftAtIsNull(String playerId, String clubId);

    /** Whether the club still has any active member -- club delete guard. */
    boolean existsByClub_IdAndLeftAtIsNull(String clubId);

    /** Every active membership for the given players, in one query (no N+1) -- see PlayerDto.clubs. */
    List<ClubMembership> findByPlayerIdInAndLeftAtIsNull(Collection<String> playerIds);

    /** Every membership, active or ended, of the given players -- the importer compares against all of them. */
    List<ClubMembership> findByPlayerIdIn(Collection<String> playerIds);

    @Query("SELECT DISTINCT m.player.id FROM ClubMembership m WHERE m.club.id = :clubId AND m.leftAt IS NULL")
    List<String> activePlayerIdsForClub(@Param("clubId") String clubId);

    @Query("SELECT DISTINCT m.player.id FROM ClubMembership m WHERE m.club.federationId = :regionId AND m.leftAt IS NULL")
    List<String> activePlayerIdsForRegion(@Param("regionId") String regionId);
}
