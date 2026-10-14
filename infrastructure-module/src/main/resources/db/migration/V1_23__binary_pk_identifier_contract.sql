-- ============================================================================
-- V1_23 — Binary PK identifier conversion: CONTRACT step (D-04 phase 2 of 2)
-- ============================================================================
-- Scope: docs/v2/adr/ADR-028-binary-pk-identifier-storage.md (결정 #5),
--        .claude/conventions/data.md#D-12, docs/v2/analysis/binary-pk-identifier-inventory.md
--
-- Precondition this file assumes (owned by a different, non-Flyway change —
-- ADR-028's "migrate" phase): the application has already been redeployed to
-- read/write the `*_bin` columns V1_22 added, so no code path still depends
-- on the original VARCHAR(128) columns this file is about to drop. This file
-- is pure DDL; it ships no app code.
--
-- What this file does, per table, in this order (constraint-ordering
-- requirement — every object dropped before its covered column changes,
-- recreated afterward with the same name and same column list):
--   1. Assert V1_22's backfill is actually complete for every column this
--      table is about to contract (sp_assert_backfill_complete). This is the
--      guard against running contract before expand finished — it SIGNALs
--      and aborts rather than silently truncating data.
--   2. Drop every INDEX/UNIQUE KEY that covers a column being swapped.
--   3. Drop the PRIMARY KEY, for tables whose PK is being swapped.
--   4. Drop the old VARCHAR(128) column(s).
--   5. Rename the `*_bin` column(s) to the original name(s).
--   6. Re-assert NOT NULL / finalize the column comment where the original
--      column was NOT NULL (PK columns, and the handful of explicit NOT NULL
--      secondary columns the inventory lists).
--   7. Recreate the PRIMARY KEY, for tables whose PK was swapped.
--   8. Recreate every INDEX/UNIQUE KEY dropped in step 2, same name, same
--      column list.
--
-- `active_marker` (timetable_occupancy, V1_21__timetable_occupancy_hold.sql:
-- 22-24, GENERATED ALWAYS AS (CASE WHEN released_at IS NULL THEN 1 ELSE
-- NULL END) STORED) is NEVER dropped or redefined by this file. Only the
-- unique key wrapping it (unique_active_occupancy) is dropped/recreated,
-- because its OTHER column (timetable_id) changes type — rebuilding
-- active_marker itself would force a needless full-table rebuild for a
-- column whose definition never changes.
--
-- No FOREIGN KEY constraints exist anywhere in this schema (inventory §4a:
-- `grep -rci 'foreign key' .../db/migration/` = 0, and all 7 @JoinColumn
-- sites declare `foreignKey = ForeignKey(NO_CONSTRAINT)`). There is
-- therefore no FK-drop/FK-recreate step in this file — there is nothing to
-- drop.
--
-- `schedule.id` (stores a copy of restaurant.id, see V1_22's header) is
-- still out of scope here, for the same reason: it is not in U1's inventory.
--
-- Idempotency / re-run safety (D-05) — MySQL has NO native IF [NOT] EXISTS
-- for PRIMARY KEY, RENAME COLUMN, ADD/DROP COLUMN, or ADD/DROP INDEX. That
-- syntax is a MariaDB-only extension; confirmed against this project's own
-- mysql:latest container, every one of ADD COLUMN IF NOT EXISTS, DROP COLUMN
-- IF EXISTS, ADD INDEX IF NOT EXISTS, ADD UNIQUE KEY IF NOT EXISTS and DROP
-- INDEX IF EXISTS is ERROR 1064 on real MySQL. (An earlier version of this
-- file claimed ADD/DROP COLUMN and ADD/DROP INDEX used "native IF [NOT]
-- EXISTS" and required "MySQL >= 8.0.29" for it — that claim was wrong and
-- is corrected here; no such MySQL feature exists at any version.) Every one
-- of those five operations is wrapped in a guard procedure that checks
-- information_schema first and skips the DDL if it was already applied by
-- an earlier, interrupted attempt:
--   - sp_drop_pk_if_exists       — no-op if the table currently has no PK
--   - sp_add_pk_if_absent        — no-op if the table already has a PK
--   - sp_rename_column_if_exists — no-op if the source column is already
--     gone (a prior attempt already renamed it)
--   - sp_drop_column_if_exists   — no-op if the column is already gone, AND
--     no-op if a column with that name exists but is already BINARY (i.e.
--     the rename already restored the final column under the same name in
--     an earlier successful run — see that procedure's own comment; this
--     second case is the one that actually matters, found by re-running
--     this file twice against a live database: without it, a full re-run
--     drops the already-converted column) (this file only ever drops
--     columns, never adds one — ADD COLUMN and its guard,
--     sp_add_column_if_absent, live in V1_22 only)
--   - sp_assert_backfill_complete — also no-op (not an error) if the
--     `*_bin` column it would check no longer exists, i.e. this column's
--     conversion already completed in an earlier run
--   - sp_add_index_if_absent     — no-op if an index/unique key with that
--     name already exists (p_unique selects UNIQUE KEY vs plain INDEX)
--   - sp_drop_index_if_exists    — no-op if no index with that name exists
-- MODIFY COLUMN is naturally idempotent (redefining a column to the same
-- type/comment it already has is never an error) and is NEVER a cast from
-- VARCHAR text to BINARY(16) here — every MODIFY COLUMN below runs AFTER
-- the RENAME COLUMN step, on a column that is already BINARY(16) (the
-- renamed `*_bin` column); it only finalizes NOT NULL / the permanent
-- comment. No guard is needed for it.
--
-- Minimum version: MySQL >= 8.0 (information_schema.COLUMNS/STATISTICS/
-- TABLE_CONSTRAINTS, stored procedures with dynamic SQL via PREPARE/EXECUTE
-- — no version-gated DDL syntax is used anywhere in this file). Same note as
-- V1_22; verified against the same mysql:latest container (server
-- `26.7.0`, `MySQL Community Server - GPL`).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Step 0: helper procedures
-- ----------------------------------------------------------------------------

DROP PROCEDURE IF EXISTS prototype_reservation.sp_assert_backfill_complete;

CREATE PROCEDURE prototype_reservation.sp_assert_backfill_complete(
    IN p_schema VARCHAR(64),
    IN p_table VARCHAR(64),
    IN p_src_col VARCHAR(64),
    IN p_dst_col VARCHAR(64)
)
BEGIN
    DECLARE v_dst_exists INT;
    DECLARE v_remaining INT;
    -- Idempotency (D-05), found by actually re-running this file twice in a
    -- row against a live database: if `p_dst_col` (the `*_bin` sibling) no
    -- longer exists, this table's conversion for this column already
    -- completed in an earlier run (DROP COLUMN + RENAME COLUMN already
    -- happened below). Without this check, a second run of this assert
    -- fails with "Unknown column '<dst>_bin'" — not a SIGNAL, a raw SQL
    -- error — because the dynamic SQL references a column that is no
    -- longer there. Checking existence first turns "already converted"
    -- into a no-op instead of an error, and — critically — lets the
    -- per-table DROP COLUMN step below stay guarded too (see
    -- sp_drop_column_if_exists's own DATA_TYPE check): skipping the assert
    -- here is paired with that guard so a full re-run never reaches a DROP
    -- COLUMN on the already-converted (BINARY16) column.
    SELECT COUNT(*) INTO v_dst_exists
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = p_schema
       AND TABLE_NAME = p_table
       AND COLUMN_NAME = p_dst_col;
    IF v_dst_exists > 0 THEN
        SET @sql = CONCAT(
            'SELECT COUNT(*) INTO @binary_pk_remaining FROM `', p_schema, '`.`', p_table, '`',
            ' WHERE `', p_dst_col, '` IS NULL AND `', p_src_col, '` IS NOT NULL'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET v_remaining = @binary_pk_remaining;
        IF v_remaining > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'binary-pk contract blocked: V1_22 backfill incomplete for this column — re-run V1_22 (or its sp_backfill_uuid_binary CALL) before contracting';
        END IF;
    END IF;
END;

DROP PROCEDURE IF EXISTS prototype_reservation.sp_drop_pk_if_exists;

CREATE PROCEDURE prototype_reservation.sp_drop_pk_if_exists(
    IN p_schema VARCHAR(64),
    IN p_table VARCHAR(64)
)
BEGIN
    DECLARE v_exists INT;
    SELECT COUNT(*) INTO v_exists
      FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = p_schema
       AND TABLE_NAME = p_table
       AND CONSTRAINT_TYPE = 'PRIMARY KEY';
    IF v_exists > 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_schema, '`.`', p_table, '` DROP PRIMARY KEY');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END;

DROP PROCEDURE IF EXISTS prototype_reservation.sp_add_pk_if_absent;

CREATE PROCEDURE prototype_reservation.sp_add_pk_if_absent(
    IN p_schema VARCHAR(64),
    IN p_table VARCHAR(64),
    IN p_cols VARCHAR(256)
)
BEGIN
    DECLARE v_exists INT;
    SELECT COUNT(*) INTO v_exists
      FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = p_schema
       AND TABLE_NAME = p_table
       AND CONSTRAINT_TYPE = 'PRIMARY KEY';
    IF v_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_schema, '`.`', p_table, '` ADD PRIMARY KEY (', p_cols, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END;

DROP PROCEDURE IF EXISTS prototype_reservation.sp_rename_column_if_exists;

CREATE PROCEDURE prototype_reservation.sp_rename_column_if_exists(
    IN p_schema VARCHAR(64),
    IN p_table VARCHAR(64),
    IN p_old_col VARCHAR(64),
    IN p_new_col VARCHAR(64)
)
BEGIN
    DECLARE v_exists INT;
    SELECT COUNT(*) INTO v_exists
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = p_schema
       AND TABLE_NAME = p_table
       AND COLUMN_NAME = p_old_col;
    IF v_exists > 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_schema, '`.`', p_table, '` RENAME COLUMN `', p_old_col, '` TO `', p_new_col, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END;

DROP PROCEDURE IF EXISTS prototype_reservation.sp_drop_column_if_exists;

-- DROP-COLUMN guard — MySQL has no `DROP COLUMN IF EXISTS` (MariaDB-only;
-- see header note above). Mirrors sp_rename_column_if_exists's check.
-- DROP-COLUMN guard, specialized for this file's one use: dropping the
-- PRE-conversion VARCHAR(128) identifier column, never a generic column.
-- That specialization matters for re-run safety in a way a plain
-- existence check does not cover — found by actually re-running this file
-- twice in a row against a live database:
--
-- Every column this file drops is later RENAMED BACK to the exact same
-- name (`id_bin` -> `id`, etc. — see "What this file does" step 5 above).
-- So after a FULLY successful run, a column named `p_col` (e.g. `id`)
-- EXISTS AGAIN — it is now the final BINARY(16) column, not the old
-- VARCHAR(128) one. A plain "DROP COLUMN IF EXISTS `id`" guard would see
-- that `id` exists and drop it — permanently destroying the very data this
-- migration converted. The DATA_TYPE check below is what makes this
-- correctly idempotent instead of destructive: it only drops the column
-- when it is still VARCHAR (pre-conversion); once it is BINARY (post-
-- conversion, after a prior successful run), this is a no-op.
CREATE PROCEDURE prototype_reservation.sp_drop_column_if_exists(
    IN p_schema VARCHAR(64),
    IN p_table VARCHAR(64),
    IN p_col VARCHAR(64)
)
BEGIN
    DECLARE v_exists INT;
    SELECT COUNT(*) INTO v_exists
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = p_schema
       AND TABLE_NAME = p_table
       AND COLUMN_NAME = p_col
       AND DATA_TYPE <> 'binary';
    IF v_exists > 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_schema, '`.`', p_table, '` DROP COLUMN `', p_col, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END;

DROP PROCEDURE IF EXISTS prototype_reservation.sp_drop_index_if_exists;

-- DROP-INDEX guard — MySQL has no `DROP INDEX IF EXISTS` (MariaDB-only).
-- Covers both plain INDEX and UNIQUE KEY (both surface identically in
-- information_schema.STATISTICS, keyed by INDEX_NAME).
CREATE PROCEDURE prototype_reservation.sp_drop_index_if_exists(
    IN p_schema VARCHAR(64),
    IN p_table VARCHAR(64),
    IN p_index_name VARCHAR(64)
)
BEGIN
    DECLARE v_exists INT;
    SELECT COUNT(*) INTO v_exists
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = p_schema
       AND TABLE_NAME = p_table
       AND INDEX_NAME = p_index_name;
    IF v_exists > 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_schema, '`.`', p_table, '` DROP INDEX `', p_index_name, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END;

DROP PROCEDURE IF EXISTS prototype_reservation.sp_add_index_if_absent;

-- ADD-INDEX guard — MySQL has no `ADD INDEX/UNIQUE KEY IF NOT EXISTS`
-- (MariaDB-only). `p_unique` = 1 emits `ADD UNIQUE KEY`, 0 emits `ADD INDEX`;
-- `p_cols` is the raw, already-backtick-quoted column list, e.g.
-- "`restaurant_id`, `date`, `start_time`, `ticket_id`".
CREATE PROCEDURE prototype_reservation.sp_add_index_if_absent(
    IN p_schema VARCHAR(64),
    IN p_table VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_cols VARCHAR(512),
    IN p_unique TINYINT(1)
)
BEGIN
    DECLARE v_exists INT;
    SELECT COUNT(*) INTO v_exists
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = p_schema
       AND TABLE_NAME = p_table
       AND INDEX_NAME = p_index_name;
    IF v_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE `', p_schema, '`.`', p_table, '` ADD ',
            IF(p_unique = 1, 'UNIQUE KEY', 'INDEX'),
            ' `', p_index_name, '` (', p_cols, ')'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END;

-- ----------------------------------------------------------------------------
-- Table: user (inventory §1 #13) — PK id only
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'user', 'id', 'id_bin');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'user');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'user', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'user', 'id_bin', 'id');
ALTER TABLE prototype_reservation.`user` MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'user', 'id');

-- ----------------------------------------------------------------------------
-- Table: user_change_history (inventory §1 #12 PK, §2a #3 user_uuid)
-- user_id (login id) and log_id (dead column) are LEAVE-AS-VARCHAR and are
-- not referenced anywhere below.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'user_change_history', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'user_change_history', 'user_uuid', 'user_uuid_bin');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'user_change_history');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'user_change_history', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'user_change_history', 'user_uuid');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'user_change_history', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'user_change_history', 'user_uuid_bin', 'user_uuid');
ALTER TABLE prototype_reservation.user_change_history MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
-- PII tag carried forward permanently onto the final column name (data.md
-- D-01, security.md S-07) — not just on the transitional *_bin column.
ALTER TABLE prototype_reservation.user_change_history MODIFY COLUMN user_uuid BINARY(16) NULL
    COMMENT 'PII:Y Sensitivity:high Purpose:사용자 식별자 참조(변경 이력, user.id) (data.md D-01, security.md S-07)';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'user_change_history', 'id');

-- ----------------------------------------------------------------------------
-- Table: user_access_history (inventory §1 #11 PK, §2a #2 user_uuid)
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'user_access_history', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'user_access_history', 'user_uuid', 'user_uuid_bin');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'user_access_history');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'user_access_history', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'user_access_history', 'user_uuid');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'user_access_history', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'user_access_history', 'user_uuid_bin', 'user_uuid');
ALTER TABLE prototype_reservation.user_access_history MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.user_access_history MODIFY COLUMN user_uuid BINARY(16) NULL
    COMMENT 'PII:Y Sensitivity:high Purpose:사용자 식별자 참조(접근 이력, user.id) (data.md D-01, security.md S-07)';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'user_access_history', 'id');

-- ----------------------------------------------------------------------------
-- Table: withdrawal_user (inventory §1 #9) — PK only; login_id untouched
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'withdrawal_user', 'id', 'id_bin');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'withdrawal_user');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'withdrawal_user', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'withdrawal_user', 'id_bin', 'id');
ALTER TABLE prototype_reservation.withdrawal_user MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'withdrawal_user', 'id');

-- ----------------------------------------------------------------------------
-- Table: company (inventory §1 #18) — PK only
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'company', 'id', 'id_bin');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'company');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'company', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'company', 'id_bin', 'id');
ALTER TABLE prototype_reservation.company MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'company', 'id');

-- ----------------------------------------------------------------------------
-- Table: restaurant (inventory §1 #1 PK, §2c #7 company_id, §2c #8 user_id)
-- No index covers company_id/user_id, so no index drop/recreate needed here.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant', 'company_id', 'company_id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant', 'user_id', 'user_id_bin');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'restaurant');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant', 'company_id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant', 'user_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant', 'company_id_bin', 'company_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant', 'user_id_bin', 'user_id');
ALTER TABLE prototype_reservation.restaurant MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.restaurant MODIFY COLUMN company_id BINARY(16) NULL COMMENT 'company 식별키';
ALTER TABLE prototype_reservation.restaurant MODIFY COLUMN user_id BINARY(16) NULL COMMENT 'user 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'restaurant', 'id');

-- ----------------------------------------------------------------------------
-- Table: restaurant_photo (inventory §1 #5 PK, §3a #3 restaurant_id)
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant_photo', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant_photo', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'restaurant_photo');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant_photo', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant_photo', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant_photo', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant_photo', 'restaurant_id_bin', 'restaurant_id');
ALTER TABLE prototype_reservation.restaurant_photo MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.restaurant_photo MODIFY COLUMN restaurant_id BINARY(16) NULL COMMENT '음식점 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'restaurant_photo', 'id');

-- ----------------------------------------------------------------------------
-- Table: restaurant_working_day (inventory §2a #1 / §3a #1) — composite PK
-- (restaurant_id, day). `day` (ENUM) is untouched; only restaurant_id swaps.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant_working_day', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'restaurant_working_day');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant_working_day', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant_working_day', 'restaurant_id_bin', 'restaurant_id');
ALTER TABLE prototype_reservation.restaurant_working_day MODIFY COLUMN restaurant_id BINARY(16) NOT NULL COMMENT '음식점 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'restaurant_working_day', 'restaurant_id, day');

-- ----------------------------------------------------------------------------
-- Table: restaurant_tags (inventory §1 #2 PK, §3a #2 restaurant_id)
-- index_restaurant_category covers restaurant_id — drop before, recreate
-- after, same name and column list (restaurant_id, tags_id).
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant_tags', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant_tags', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'restaurant_tags', 'index_restaurant_category');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'restaurant_tags');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant_tags', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant_tags', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant_tags', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant_tags', 'restaurant_id_bin', 'restaurant_id');
ALTER TABLE prototype_reservation.restaurant_tags MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.restaurant_tags MODIFY COLUMN restaurant_id BINARY(16) NULL COMMENT '음식점 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'restaurant_tags', 'id');
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'restaurant_tags', 'index_restaurant_category', '`restaurant_id`, `tags_id`', 0);

-- ----------------------------------------------------------------------------
-- Table: restaurant_nationalities (inventory §1 #4 PK, §3a #4 restaurant_id)
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant_nationalities', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant_nationalities', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'restaurant_nationalities', 'index_restaurant_category');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'restaurant_nationalities');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant_nationalities', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant_nationalities', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant_nationalities', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant_nationalities', 'restaurant_id_bin', 'restaurant_id');
ALTER TABLE prototype_reservation.restaurant_nationalities MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.restaurant_nationalities MODIFY COLUMN restaurant_id BINARY(16) NULL COMMENT '음식점 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'restaurant_nationalities', 'id');
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'restaurant_nationalities', 'index_restaurant_category', '`restaurant_id`, `nationalities_id`', 0);

-- ----------------------------------------------------------------------------
-- Table: restaurant_cuisines (inventory §1 #3 PK, §3a #5 restaurant_id)
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant_cuisines', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'restaurant_cuisines', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'restaurant_cuisines', 'index_restaurant_category');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'restaurant_cuisines');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant_cuisines', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'restaurant_cuisines', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant_cuisines', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'restaurant_cuisines', 'restaurant_id_bin', 'restaurant_id');
ALTER TABLE prototype_reservation.restaurant_cuisines MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.restaurant_cuisines MODIFY COLUMN restaurant_id BINARY(16) NULL COMMENT '음식점 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'restaurant_cuisines', 'id');
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'restaurant_cuisines', 'index_restaurant_category', '`restaurant_id`, `cuisines_id`', 0);

-- ----------------------------------------------------------------------------
-- Table: menu (inventory §1 #14 PK, §2c #10 restaurant_id)
-- index_restaurant_id covers restaurant_id.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'menu', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'menu', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'menu', 'index_restaurant_id');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'menu');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'menu', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'menu', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'menu', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'menu', 'restaurant_id_bin', 'restaurant_id');
ALTER TABLE prototype_reservation.menu MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.menu MODIFY COLUMN restaurant_id BINARY(16) NULL COMMENT '음식점 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'menu', 'id');
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'menu', 'index_restaurant_id', '`restaurant_id`', 0);

-- ----------------------------------------------------------------------------
-- Table: menu_photo (inventory §1 #15 PK, §3a #6 menu_id)
-- index_menu_id covers menu_id.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'menu_photo', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'menu_photo', 'menu_id', 'menu_id_bin');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'menu_photo', 'index_menu_id');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'menu_photo');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'menu_photo', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'menu_photo', 'menu_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'menu_photo', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'menu_photo', 'menu_id_bin', 'menu_id');
ALTER TABLE prototype_reservation.menu_photo MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.menu_photo MODIFY COLUMN menu_id BINARY(16) NULL COMMENT '메뉴 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'menu_photo', 'id');
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'menu_photo', 'index_menu_id', '`menu_id`', 0);

-- ----------------------------------------------------------------------------
-- Table: time_span (inventory §1 #8 PK, §2c #13 restaurant_id NOT NULL)
-- index_restaurant_id covers restaurant_id.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'time_span', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'time_span', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'time_span', 'index_restaurant_id');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'time_span');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'time_span', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'time_span', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'time_span', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'time_span', 'restaurant_id_bin', 'restaurant_id');
ALTER TABLE prototype_reservation.time_span MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.time_span MODIFY COLUMN restaurant_id BINARY(16) NOT NULL COMMENT '음식점 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'time_span', 'id');
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'time_span', 'index_restaurant_id', '`restaurant_id`', 0);

-- ----------------------------------------------------------------------------
-- Table: holiday (inventory §1 #6 PK, §2c #11 restaurant_id NOT NULL)
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'holiday', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'holiday', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'holiday');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'holiday', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'holiday', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'holiday', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'holiday', 'restaurant_id_bin', 'restaurant_id');
ALTER TABLE prototype_reservation.holiday MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.holiday MODIFY COLUMN restaurant_id BINARY(16) NOT NULL COMMENT '음식점 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'holiday', 'id');

-- ----------------------------------------------------------------------------
-- Table: table (inventory §1 #7 PK, §2c #12 restaurant_id NOT NULL) —
-- reserved word, backtick-quoted throughout.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'table', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'table', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'table');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'table', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'table', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'table', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'table', 'restaurant_id_bin', 'restaurant_id');
ALTER TABLE prototype_reservation.`table` MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키';
ALTER TABLE prototype_reservation.`table` MODIFY COLUMN restaurant_id BINARY(16) NOT NULL COMMENT '음식점 식별키';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'table', 'id');

-- ----------------------------------------------------------------------------
-- Table: timetable (inventory §1 #17 PK, §2c #9 restaurant_id)
-- index_restaurant_date_start_time_table_status covers restaurant_id.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'timetable', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'timetable', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'timetable', 'index_restaurant_date_start_time_table_status');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'timetable');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'timetable', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'timetable', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'timetable', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'timetable', 'restaurant_id_bin', 'restaurant_id');
ALTER TABLE prototype_reservation.timetable MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '시간표 ID';
ALTER TABLE prototype_reservation.timetable MODIFY COLUMN restaurant_id BINARY(16) NULL COMMENT '매장 ID';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'timetable', 'id');
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'timetable', 'index_restaurant_date_start_time_table_status', '`restaurant_id`, `date`, `start_time`, `table_status`', 0);

-- ----------------------------------------------------------------------------
-- Table: timetable_occupancy (inventory §1 #16 PK, §3a #7 timetable_id,
-- §2c #14 user_id). Two constraints cover timetable_id:
-- index_timetable_id_occupied_status and unique_active_occupancy. Both are
-- dropped before the swap and recreated after. `active_marker` itself is
-- NEVER dropped, modified, or redefined — only the unique key wrapping it.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'timetable_occupancy', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'timetable_occupancy', 'timetable_id', 'timetable_id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'timetable_occupancy', 'user_id', 'user_id_bin');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'timetable_occupancy', 'index_timetable_id_occupied_status');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'timetable_occupancy', 'unique_active_occupancy');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'timetable_occupancy');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'timetable_occupancy', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'timetable_occupancy', 'timetable_id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'timetable_occupancy', 'user_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'timetable_occupancy', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'timetable_occupancy', 'timetable_id_bin', 'timetable_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'timetable_occupancy', 'user_id_bin', 'user_id');
ALTER TABLE prototype_reservation.timetable_occupancy MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '시간표 점유 ID';
ALTER TABLE prototype_reservation.timetable_occupancy MODIFY COLUMN timetable_id BINARY(16) NULL COMMENT '시간표 ID';
ALTER TABLE prototype_reservation.timetable_occupancy MODIFY COLUMN user_id BINARY(16) NULL COMMENT '사용자 ID';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'timetable_occupancy', 'id');
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'timetable_occupancy', 'index_timetable_id_occupied_status', '`timetable_id`, `occupied_status`', 0);
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'timetable_occupancy', 'unique_active_occupancy', '`timetable_id`, `active_marker`', 1);

-- ----------------------------------------------------------------------------
-- Table: reservation (inventory §1 #10 PK, §2e #18-21). Only user_id is
-- covered by an index (index_user_id_reservation_date_reservation_status);
-- restaurant_id, timetable_id, timetable_occupancy_id have no covering index.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'reservation', 'id', 'id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'reservation', 'user_id', 'user_id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'reservation', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'reservation', 'timetable_id', 'timetable_id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'reservation', 'timetable_occupancy_id', 'timetable_occupancy_id_bin');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'reservation', 'index_user_id_reservation_date_reservation_status');
CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'reservation');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'reservation', 'id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'reservation', 'user_id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'reservation', 'restaurant_id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'reservation', 'timetable_id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'reservation', 'timetable_occupancy_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'reservation', 'id_bin', 'id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'reservation', 'user_id_bin', 'user_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'reservation', 'restaurant_id_bin', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'reservation', 'timetable_id_bin', 'timetable_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'reservation', 'timetable_occupancy_id_bin', 'timetable_occupancy_id');
ALTER TABLE prototype_reservation.reservation MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '예약 PK';
ALTER TABLE prototype_reservation.reservation MODIFY COLUMN user_id BINARY(16) NULL COMMENT '사용자 ID';
ALTER TABLE prototype_reservation.reservation MODIFY COLUMN restaurant_id BINARY(16) NULL COMMENT '매장 ID';
ALTER TABLE prototype_reservation.reservation MODIFY COLUMN timetable_id BINARY(16) NULL COMMENT '시간표 ID';
ALTER TABLE prototype_reservation.reservation MODIFY COLUMN timetable_occupancy_id BINARY(16) NULL COMMENT '시간표 점유 ID';
CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'reservation', 'id');
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'reservation', 'index_user_id_reservation_date_reservation_status', '`user_id`, `reservation_date`, `reservation_status`', 0);

-- ----------------------------------------------------------------------------
-- Table: waiting_queue (inventory §2d #15 restaurant_id, #16 user_id — both
-- NOT NULL). `id` (BIGINT AUTO_INCREMENT) is NOT a UUID identifier and its
-- PRIMARY KEY is NEVER dropped or recreated here. `ticket_id` (SecureRandom
-- nonce, §2d #17) is LEAVE-AS-VARCHAR and untouched. Three constraints cover
-- restaurant_id and/or user_id: unique_slot_ticket_id, index_slot_status_id,
-- unique_slot_user_id — all three dropped before the swap, recreated after,
-- same name and column list.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'waiting_queue', 'restaurant_id', 'restaurant_id_bin');
CALL prototype_reservation.sp_assert_backfill_complete('prototype_reservation', 'waiting_queue', 'user_id', 'user_id_bin');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'waiting_queue', 'unique_slot_ticket_id');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'waiting_queue', 'index_slot_status_id');
CALL prototype_reservation.sp_drop_index_if_exists('prototype_reservation', 'waiting_queue', 'unique_slot_user_id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'waiting_queue', 'restaurant_id');
CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'waiting_queue', 'user_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'waiting_queue', 'restaurant_id_bin', 'restaurant_id');
CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'waiting_queue', 'user_id_bin', 'user_id');
ALTER TABLE prototype_reservation.waiting_queue MODIFY COLUMN restaurant_id BINARY(16) NOT NULL COMMENT '매장 ID';
ALTER TABLE prototype_reservation.waiting_queue MODIFY COLUMN user_id BINARY(16) NOT NULL COMMENT '사용자 ID';
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'waiting_queue', 'unique_slot_ticket_id', '`restaurant_id`, `date`, `start_time`, `ticket_id`', 1);
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'waiting_queue', 'index_slot_status_id', '`restaurant_id`, `date`, `start_time`, `status`, `id`', 0);
CALL prototype_reservation.sp_add_index_if_absent('prototype_reservation', 'waiting_queue', 'unique_slot_user_id', '`restaurant_id`, `date`, `start_time`, `user_id`', 1);

-- ----------------------------------------------------------------------------
-- Step N: drop the helper procedures. Scratch infrastructure for this
-- migration only, same reasoning as V1_22's sp_backfill_uuid_binary.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS prototype_reservation.sp_assert_backfill_complete;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_drop_pk_if_exists;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_add_pk_if_absent;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_rename_column_if_exists;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_drop_column_if_exists;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_drop_index_if_exists;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_add_index_if_absent;
