CREATE TABLE `${tablePrefix}progress_player_names` (
    `normalized_username` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `player_uuid` BINARY(16) NOT NULL,
    `username` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `last_seen_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`normalized_username`),
    UNIQUE KEY `uk_progress_player_names_uuid` (`player_uuid`),
    INDEX `idx_progress_player_names_recent` (`last_seen_at` DESC, `normalized_username`),
    FOREIGN KEY (`player_uuid`) REFERENCES `${tablePrefix}progress_accounts` (`player_uuid`) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CHECK (`player_uuid` <> UNHEX('00000000000000000000000000000000')),
    CHECK (`normalized_username` REGEXP '^[a-z0-9_]{3,16}$'),
    CHECK (`username` REGEXP '^[A-Za-z0-9_]{3,16}$'),
    CHECK (`normalized_username` = LOWER(`username`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
