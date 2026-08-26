-- Write-permission grants for registered apps/service clients, keyed on the JWT azp claim --
-- independent of role_assignment (keyed on a human's dtfb_id). See docs/10 §5.
CREATE TABLE `api_client_grant` (
  `id` varchar(14) NOT NULL,
  `client_id` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `write_access` bit(1) NOT NULL,
  `scope_type` enum('GLOBAL','REGION','CLUB','TEAM','LEAGUE') NOT NULL,
  `scope_id` varchar(255) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_api_client_grant_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
