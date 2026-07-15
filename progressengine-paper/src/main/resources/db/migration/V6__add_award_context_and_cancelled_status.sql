ALTER TABLE `${tablePrefix}progress_operations`
    ADD COLUMN `game_id` VARCHAR(64) NULL AFTER `requested_amount`;

SET @progress_status_check := (
    SELECT cc.CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS tc
    JOIN information_schema.CHECK_CONSTRAINTS cc
        ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
        AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
        AND tc.TABLE_NAME = '${tablePrefix}progress_operations'
        AND tc.CONSTRAINT_TYPE = 'CHECK'
        AND cc.CHECK_CLAUSE LIKE '%NO_POINTS_AWARDED%'
        AND cc.CHECK_CLAUSE LIKE '%BALANCE_LIMIT_EXCEEDED%'
        AND cc.CHECK_CLAUSE NOT LIKE '%CANCELLED%'
    LIMIT 1
);

SET @progress_drop_status_check := IF(
    @progress_status_check IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE `${tablePrefix}progress_operations` DROP CHECK `', @progress_status_check, '`')
);

PREPARE progress_drop_status_check_statement FROM @progress_drop_status_check;
EXECUTE progress_drop_status_check_statement;
DEALLOCATE PREPARE progress_drop_status_check_statement;

ALTER TABLE `${tablePrefix}progress_operations`
    ADD CONSTRAINT `chk_progress_operations_status_v2`
    CHECK (`status` IN ('PENDING', 'SUCCESS', 'NO_POINTS_AWARDED', 'CANCELLED', 'INSUFFICIENT_FUNDS', 'BALANCE_LIMIT_EXCEEDED')),
    ADD CONSTRAINT `chk_progress_operations_game_id`
    CHECK (`game_id` IS NULL OR (`type` = 'AWARD' AND REGEXP_LIKE(`game_id`, '^[a-z0-9][a-z0-9._-]{0,63}$')));
