-- SPO-48: machine access moves from Keycloak clients + write-capable api_client_grant rows (V8)
-- to backend-issued, read-only API keys (X-API-Key header, docs/20-api-keys.md). Only the SHA-256
-- hash of a key is stored. api_client_grant is dropped: writes via API keys are no longer possible,
-- and any existing grants referred to Keycloak clients that are retired with this change.
CREATE TABLE `api_key` (
  `id` varchar(14) NOT NULL,
  `name` varchar(255) NOT NULL,
  `key_prefix` varchar(255) NOT NULL,
  `key_hash` varchar(255) NOT NULL,
  `active` bit(1) NOT NULL,
  `expires_at` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_dtfb_id` varchar(255) DEFAULT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_api_key_key_hash` (`key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP TABLE `api_client_grant`;
