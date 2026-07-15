CREATE TABLE `${tablePrefix}progress_accounts` (
    `player_uuid` BINARY(16) NOT NULL,
    `balance` BIGINT NOT NULL DEFAULT 0,
    `revision` BIGINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`player_uuid`),
    CHECK (`player_uuid` <> UNHEX('00000000000000000000000000000000')),
    CHECK (`balance` >= 0),
    CHECK (`revision` >= 0),
    CHECK (`updated_at` >= `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
