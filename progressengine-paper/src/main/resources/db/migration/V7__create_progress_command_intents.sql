CREATE TABLE `${tablePrefix}progress_command_intents` (
    `token_hash` BINARY(32) NOT NULL,
    `operation_id` BINARY(16) NOT NULL,
    `intent_type` VARCHAR(32) NOT NULL,
    `state` VARCHAR(32) NOT NULL,
    `owner_uuid` BINARY(16) NULL,
    `actor_type` VARCHAR(16) NOT NULL,
    `actor_uuid` BINARY(16) NULL,
    `player_uuid` BINARY(16) NOT NULL,
    `target_uuid` BINARY(16) NULL,
    `amount` BIGINT NOT NULL,
    `reason_key` VARCHAR(128) NOT NULL,
    `observed_revision` BIGINT NULL,
    `source_server_id` VARCHAR(64) NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `expires_at` DATETIME(6) NOT NULL,
    `submitted_at` DATETIME(6) NULL,
    `resolved_at` DATETIME(6) NULL,
    PRIMARY KEY (`token_hash`),
    UNIQUE KEY `uk_progress_command_intents_operation` (`operation_id`),
    INDEX `idx_progress_command_intents_expiry` (`expires_at`, `state`),
    INDEX `idx_progress_command_intents_owner` (`owner_uuid`, `created_at`),
    CHECK (`operation_id` <> UNHEX('00000000000000000000000000000000')),
    CHECK (`intent_type` IN ('PAY', 'ADMIN_ADD', 'ADMIN_REMOVE', 'ADMIN_SET', 'ADMIN_RESET')),
    CHECK (`state` IN ('AWAITING_CONFIRMATION', 'SUBMITTED', 'RESOLVED')),
    CHECK (`owner_uuid` IS NULL OR `owner_uuid` <> UNHEX('00000000000000000000000000000000')),
    CHECK (`actor_type` IN ('PLAYER', 'CONSOLE')),
    CHECK ((`actor_type` = 'PLAYER' AND `actor_uuid` IS NOT NULL) OR (`actor_type` <> 'PLAYER' AND `actor_uuid` IS NULL)),
    CHECK (`actor_uuid` IS NULL OR `actor_uuid` <> UNHEX('00000000000000000000000000000000')),
    CHECK (`player_uuid` <> UNHEX('00000000000000000000000000000000')),
    CHECK (`target_uuid` IS NULL OR `target_uuid` <> UNHEX('00000000000000000000000000000000')),
    CHECK (`target_uuid` IS NULL OR `target_uuid` <> `player_uuid`),
    CHECK (`amount` >= 0),
    CHECK (`observed_revision` IS NULL OR `observed_revision` > 0),
    CHECK (`expires_at` > `created_at`),
    CHECK ((`state` = 'AWAITING_CONFIRMATION' AND `submitted_at` IS NULL AND `resolved_at` IS NULL)
        OR (`state` = 'SUBMITTED' AND `submitted_at` IS NOT NULL AND `resolved_at` IS NULL)
        OR (`state` = 'RESOLVED' AND `submitted_at` IS NOT NULL AND `resolved_at` IS NOT NULL)),
    CHECK (`submitted_at` IS NULL OR `submitted_at` >= `created_at`),
    CHECK (`resolved_at` IS NULL OR `resolved_at` >= `submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE INDEX `idx_progress_operations_actor_pay_cooldown`
    ON `${tablePrefix}progress_operations` (`actor_uuid`, `type`, `status`, `completed_at` DESC);
