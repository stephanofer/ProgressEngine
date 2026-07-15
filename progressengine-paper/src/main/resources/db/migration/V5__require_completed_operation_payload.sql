ALTER TABLE `${tablePrefix}progress_operations`
    ADD CONSTRAINT `chk_progress_operations_result_state`
    CHECK (
        (`status` = 'PENDING' AND `result_version` IS NULL AND `result_json` IS NULL)
        OR (`status` <> 'PENDING' AND `result_version` IS NOT NULL AND `result_json` IS NOT NULL)
    );
