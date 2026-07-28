
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` varchar(14) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `short_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `club`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `club` (
  `id` varchar(14) NOT NULL,
  `active` bit(1) NOT NULL,
  `city` varchar(255) DEFAULT NULL,
  `federation_id` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `short_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `comp_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comp_group` (
  `id` varchar(14) NOT NULL,
  `group_state` enum('CANCELED','FINISHED','PLANNED','READY','RUNNING') NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `tier_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK24tpjuu9bcboanef1xthsx2mr` (`tier_id`),
  CONSTRAINT `FK24tpjuu9bcboanef1xthsx2mr` FOREIGN KEY (`tier_id`) REFERENCES `tier` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `federation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `federation` (
  `id` varchar(14) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `default_rule_set_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlu7ft6q23ix1ffc1iir0k3o0e` (`default_rule_set_id`),
  CONSTRAINT `FKlu7ft6q23ix1ffc1iir0k3o0e` FOREIGN KEY (`default_rule_set_id`) REFERENCES `league_rule_set` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `game_plan_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_plan_entry` (
  `id` varchar(14) NOT NULL,
  `game_type` enum('DOUBLE','GOALIE','SINGLE') DEFAULT NULL,
  `position` int DEFAULT NULL,
  `rule_set_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKswnyikk8la33fkjpmtauq1728` (`rule_set_id`),
  CONSTRAINT `FKswnyikk8la33fkjpmtauq1728` FOREIGN KEY (`rule_set_id`) REFERENCES `league_rule_set` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `league`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `league` (
  `id` varchar(14) NOT NULL,
  `import_id` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `category_id` varchar(14) DEFAULT NULL,
  `rule_set_id` varchar(14) DEFAULT NULL,
  `season_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKngtun2nuow3q8a2rvnidmgjkn` (`category_id`),
  KEY `FKrvqw9n6p8ohhwmurkor2wndyx` (`rule_set_id`),
  KEY `FKcr9wb5vi3lnfaj2tou6v3lxop` (`season_id`),
  CONSTRAINT `FKcr9wb5vi3lnfaj2tou6v3lxop` FOREIGN KEY (`season_id`) REFERENCES `season` (`id`),
  CONSTRAINT `FKngtun2nuow3q8a2rvnidmgjkn` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`),
  CONSTRAINT `FKrvqw9n6p8ohhwmurkor2wndyx` FOREIGN KEY (`rule_set_id`) REFERENCES `league_rule_set` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `league_rule_set`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `league_rule_set` (
  `id` varchar(14) NOT NULL,
  `matchday_decision` enum('ALL_GAMES','FIRST_TO') DEFAULT NULL,
  `matchday_target` int DEFAULT NULL,
  `max_roster_size` int DEFAULT NULL,
  `min_roster_size` int DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `play_system` enum('ROUND_ROBIN','SWISS') DEFAULT NULL,
  `points_draw` int DEFAULT NULL,
  `points_loss` int DEFAULT NULL,
  `points_to_win_set` int DEFAULT NULL,
  `points_win` int DEFAULT NULL,
  `scheduling_mode` enum('DAY_BATCH','WINDOW') DEFAULT NULL,
  `scheduling_window_days` int DEFAULT NULL,
  `sets_per_game` int DEFAULT NULL,
  `side_switch_allowed` bit(1) DEFAULT NULL,
  `federation_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKxy5nrfrav8jfv0cafnb9l4x2` (`federation_id`),
  CONSTRAINT `FKxy5nrfrav8jfv0cafnb9l4x2` FOREIGN KEY (`federation_id`) REFERENCES `federation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `location`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `location` (
  `id` varchar(14) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `federation_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKi6n5a7tcvrr3uc3qhqd64ihqc` (`federation_id`),
  CONSTRAINT `FKi6n5a7tcvrr3uc3qhqd64ihqc` FOREIGN KEY (`federation_id`) REFERENCES `federation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `match_day`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `match_day` (
  `id` varchar(14) NOT NULL,
  `away_confirmed_at` datetime(6) DEFAULT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `home_confirmed_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `result_state` enum('CONFIRMED','HOME_SUBMITTED','OPEN') NOT NULL,
  `schedule_confirmed_at` datetime(6) DEFAULT NULL,
  `schedule_proposed_by_dtfb_id` varchar(255) DEFAULT NULL,
  `scheduling_state` enum('CONFIRMED','DEFAULT','PROPOSED') NOT NULL,
  `start_date` datetime(6) NOT NULL,
  `submitted_by_dtfb_id` varchar(255) DEFAULT NULL,
  `location_id` varchar(14) DEFAULT NULL,
  `round_id` varchar(14) DEFAULT NULL,
  `team_away_id` varchar(14) DEFAULT NULL,
  `team_home_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKtk4ted7itedgwlf7n7lk3evjx` (`location_id`),
  KEY `FK9arahbxedbti468tvy7po2yn5` (`round_id`),
  KEY `FKe0gkxxw4ph39nqpnb33q3trrr` (`team_away_id`),
  KEY `FKl1seeuayqtf7naq5v2e186764` (`team_home_id`),
  CONSTRAINT `FK9arahbxedbti468tvy7po2yn5` FOREIGN KEY (`round_id`) REFERENCES `round` (`id`),
  CONSTRAINT `FKe0gkxxw4ph39nqpnb33q3trrr` FOREIGN KEY (`team_away_id`) REFERENCES `team` (`id`),
  CONSTRAINT `FKl1seeuayqtf7naq5v2e186764` FOREIGN KEY (`team_home_id`) REFERENCES `team` (`id`),
  CONSTRAINT `FKtk4ted7itedgwlf7n7lk3evjx` FOREIGN KEY (`location_id`) REFERENCES `location` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `match_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `match_event` (
  `id` varchar(14) NOT NULL,
  `away_score` int DEFAULT NULL,
  `home_score` int DEFAULT NULL,
  `json` longtext,
  `player_id` varchar(255) DEFAULT NULL,
  `timestamp` datetime(6) DEFAULT NULL,
  `type` enum('CARD','END','GOAL','OTHER','OWN_GOAL','START','TIMEOUT') DEFAULT NULL,
  `match_id` varchar(14) DEFAULT NULL,
  `team_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKl3t5aaqw9dunbuewxfqf6oxj0` (`match_id`),
  KEY `FKl8mesg52cgwimxc5nlqdj89t7` (`team_id`),
  CONSTRAINT `FKl3t5aaqw9dunbuewxfqf6oxj0` FOREIGN KEY (`match_id`) REFERENCES `match_game` (`id`),
  CONSTRAINT `FKl8mesg52cgwimxc5nlqdj89t7` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `match_game`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `match_game` (
  `id` varchar(14) NOT NULL,
  `away_score` int DEFAULT NULL,
  `end_time` datetime(6) DEFAULT NULL,
  `home_score` int DEFAULT NULL,
  `position` int DEFAULT NULL,
  `start_time` datetime(6) NOT NULL,
  `state` enum('BYE','INCOMPLETE','OPEN','PAUSED','PLANNED','PLAYED','RUNNING','SKIPPED') DEFAULT NULL,
  `type` enum('DOUBLE','GOALIE','SINGLE') DEFAULT NULL,
  `winner` enum('AWAY','DRAW','HOME') DEFAULT NULL,
  `match_day_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKn19cwauy6a3y9j04tpfeq98dw` (`match_day_id`),
  CONSTRAINT `FKn19cwauy6a3y9j04tpfeq98dw` FOREIGN KEY (`match_day_id`) REFERENCES `match_day` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `match_set`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `match_set` (
  `id` varchar(14) NOT NULL,
  `away_score` int DEFAULT NULL,
  `home_score` int DEFAULT NULL,
  `set_number` int DEFAULT NULL,
  `match_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3y7exc6dvelb32n1f73bg2udl` (`match_id`),
  CONSTRAINT `FK3y7exc6dvelb32n1f73bg2udl` FOREIGN KEY (`match_id`) REFERENCES `match_game` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `player`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `player` (
  `id` varchar(14) NOT NULL,
  `active` bit(1) NOT NULL,
  `birth_year` int DEFAULT NULL,
  `dtfb_id` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `first_name` varchar(255) DEFAULT NULL,
  `gender` varchar(255) DEFAULT NULL,
  `international_id` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `national_id` varchar(255) DEFAULT NULL,
  `national_license` varchar(255) DEFAULT NULL,
  `nationality` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmudwa1n9gtkvobjk5ccc97vtl` (`dtfb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `role_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_assignment` (
  `id` varchar(14) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `granted_by_dtfb_id` varchar(255) DEFAULT NULL,
  `role` enum('ADMIN','CLUB_ADMIN','LEAGUE_ADMIN','REGION_ADMIN','REGION_TOURNAMENT_UPLOADER','TEAM_ADMIN','TOURNAMENT_UPLOADER') NOT NULL,
  `scope_id` varchar(255) DEFAULT NULL,
  `scope_type` enum('CLUB','GLOBAL','LEAGUE','REGION','TEAM') NOT NULL,
  `player_id` varchar(14) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKiuagbx71ubywl5uvru90u5t8w` (`player_id`),
  CONSTRAINT `FKiuagbx71ubywl5uvru90u5t8w` FOREIGN KEY (`player_id`) REFERENCES `player` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `roster_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roster_entry` (
  `id` varchar(14) NOT NULL,
  `added_at` datetime(6) NOT NULL,
  `removed_at` datetime(6) DEFAULT NULL,
  `participation_id` varchar(14) DEFAULT NULL,
  `player_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKal6q47vds66cr805bchsw1h84` (`participation_id`),
  KEY `FKqmv4k184l03r8r7tq7ajtj1eb` (`player_id`),
  CONSTRAINT `FKal6q47vds66cr805bchsw1h84` FOREIGN KEY (`participation_id`) REFERENCES `team_participation` (`id`),
  CONSTRAINT `FKqmv4k184l03r8r7tq7ajtj1eb` FOREIGN KEY (`player_id`) REFERENCES `player` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `round`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `round` (
  `id` varchar(14) NOT NULL,
  `round_index` int DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `window_end` datetime(6) DEFAULT NULL,
  `window_start` datetime(6) DEFAULT NULL,
  `group_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK76yhem794es0cwgyy78a4myhk` (`group_id`),
  CONSTRAINT `FK76yhem794es0cwgyy78a4myhk` FOREIGN KEY (`group_id`) REFERENCES `comp_group` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `season`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `season` (
  `id` varchar(14) NOT NULL,
  `archived_at` datetime(6) DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `registration_closes_at` date DEFAULT NULL,
  `registration_opens_at` date DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `federation_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhkq1tyg39jwy19xveluigi6o2` (`federation_id`),
  CONSTRAINT `FKhkq1tyg39jwy19xveluigi6o2` FOREIGN KEY (`federation_id`) REFERENCES `federation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `standing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `standing` (
  `id` varchar(14) NOT NULL,
  `draws` int NOT NULL,
  `losses` int NOT NULL,
  `played` int NOT NULL,
  `points` int NOT NULL,
  `sets_lost` int NOT NULL,
  `sets_won` int NOT NULL,
  `wins` int NOT NULL,
  `group_id` varchar(14) NOT NULL,
  `team_id` varchar(14) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2u3n34mrsngwa5r9jx2xhptv1` (`group_id`,`team_id`),
  KEY `FKk0dfm7sbpoa92k84uygyb1iax` (`team_id`),
  CONSTRAINT `FKk0dfm7sbpoa92k84uygyb1iax` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`),
  CONSTRAINT `FKl8ns6w61yw3ob1vxk2b8ise2x` FOREIGN KEY (`group_id`) REFERENCES `comp_group` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `team`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team` (
  `id` varchar(14) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `club_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnl01gosacvic5nvy7gq3h7v2y` (`club_id`),
  CONSTRAINT `FKnl01gosacvic5nvy7gq3h7v2y` FOREIGN KEY (`club_id`) REFERENCES `club` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `team_participation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team_participation` (
  `id` varchar(14) NOT NULL,
  `copied_from_participation_id` varchar(255) DEFAULT NULL,
  `roster_status` enum('CONFIRMED','DRAFT','SUBMITTED') NOT NULL,
  `status` enum('ACTIVE','WITHDRAWN') NOT NULL,
  `withdrawn_at` datetime(6) DEFAULT NULL,
  `group_id` varchar(14) DEFAULT NULL,
  `league_id` varchar(14) DEFAULT NULL,
  `team_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfgeib23fxwik51k0bst788he5` (`group_id`),
  KEY `FKmiyasdtnlky9f6gyfqjiavuff` (`league_id`),
  KEY `FK654ggc2ewr9muvridgrhx1l3f` (`team_id`),
  CONSTRAINT `FK654ggc2ewr9muvridgrhx1l3f` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`),
  CONSTRAINT `FKfgeib23fxwik51k0bst788he5` FOREIGN KEY (`group_id`) REFERENCES `comp_group` (`id`),
  CONSTRAINT `FKmiyasdtnlky9f6gyfqjiavuff` FOREIGN KEY (`league_id`) REFERENCES `league` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tier`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tier` (
  `id` varchar(14) NOT NULL,
  `level` int DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `league_id` varchar(14) DEFAULT NULL,
  `rule_set_id` varchar(14) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhq7y9mlnoodvcf82lh90y3qom` (`league_id`),
  KEY `FK8t9nyct6stktteq9agassa2sr` (`rule_set_id`),
  CONSTRAINT `FK8t9nyct6stktteq9agassa2sr` FOREIGN KEY (`rule_set_id`) REFERENCES `league_rule_set` (`id`),
  CONSTRAINT `FKhq7y9mlnoodvcf82lh90y3qom` FOREIGN KEY (`league_id`) REFERENCES `league` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tracker_issue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tracker_issue` (
  `id` varchar(14) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `github_issue_url` varchar(255) DEFAULT NULL,
  `github_repo` varchar(255) DEFAULT NULL,
  `reported_by` varchar(255) DEFAULT NULL,
  `status` enum('APPROVED','OPEN') NOT NULL,
  `title` varchar(200) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tracker_issue_vote`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tracker_issue_vote` (
  `id` varchar(14) NOT NULL,
  `voter_name` varchar(255) NOT NULL,
  `issue_id` varchar(14) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmxknp0f41348j1ng79e13i29h` (`issue_id`,`voter_name`),
  CONSTRAINT `FKia3np9cc4r4ogy5cu5tpbi4s4` FOREIGN KEY (`issue_id`) REFERENCES `tracker_issue` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

