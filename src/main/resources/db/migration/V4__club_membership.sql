-- Player <-> Club membership: the precondition for a player being rostered onto one of a club's
-- teams (see docs -- RosterService#addPlayer now enforces this). Independent of team rosters; a
-- player may hold several active memberships (multiple clubs) at once.
--
-- Same shape as roster_entry: joined_at + soft-delete left_at, not a hard delete, so membership
-- history is kept.

CREATE TABLE `club_membership` (
  `id` varchar(14) NOT NULL,
  `player_id` varchar(14) DEFAULT NULL,
  `club_id` varchar(14) DEFAULT NULL,
  `joined_at` datetime NOT NULL,
  `left_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IX_club_membership_player` (`player_id`),
  KEY `IX_club_membership_club` (`club_id`),
  CONSTRAINT `FK_club_membership_player` FOREIGN KEY (`player_id`) REFERENCES `player` (`id`),
  CONSTRAINT `FK_club_membership_club` FOREIGN KEY (`club_id`) REFERENCES `club` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
