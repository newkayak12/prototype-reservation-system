-- ============================================================================
-- V1_24 — Binary PK identifier conversion: schedule.id (gap closed from V1_22/V1_23)
-- ============================================================================
-- Scope: docs/v2/adr/ADR-028-binary-pk-identifier-storage.md (결정 #5),
--        .claude/conventions/data.md#D-12, D-05,
--        V1_22__binary_pk_identifier_expand.sql (schedule.id 를 범위 밖으로
--        남긴 원문), V1_23__binary_pk_identifier_contract.sql (같은 결정 재확인)
--
-- What this file does, and nothing else:
--   Converts `schedule.id` (VARCHAR(255), PRIMARY KEY — V1_13__schedule_
--   context.sql:3 `id VARCHAR(255) PRIMARY KEY`) to BINARY(16).
--   `schedule.id` stores a copy of `restaurant.id`
--   (ScheduleEntity.kt — `@Id @Column(name = "id") val restaurantId: String`,
--   not a TimeBasedPrimaryKey subclass, so it was not enumerated in
--   binary-pk-identifier-inventory.md's 41-column sweep and V1_22/V1_23
--   deliberately left it untouched — see V1_22's header "What this file
--   deliberately does NOT do" and V1_23's header "schedule.id ... is still
--   out of scope here"). This file closes that recorded gap, using the exact
--   same expand -> backfill -> assert -> contract mechanism V1_22/V1_23 used
--   for the other 41 columns (D-04/D-12), reusing the same guard-procedure
--   shapes rather than inventing a new pattern.
--
-- What this file deliberately does NOT do:
--   - It does not add a FOREIGN KEY between schedule.id and restaurant.id
--     (data.md D-07 — explicitly out of scope; no FK exists anywhere in this
--     schema today, see V1_23's header, "No FOREIGN KEY constraints exist
--     anywhere in this schema").
--   - It does not touch any other table, column, or index. `schedule`'s
--     other columns (tables_configured, working_hours_configured,
--     holidays_configured, status, total_tables, total_capacity) are not
--     identifier columns and are untouched.
--   - It does not pursue true zero-downtime/online rollout. ADR-028 already
--     records the expand/contract-across-separate-deploys limitation for the
--     other 41 columns (V1_22 run first, app redeployed to the *_bin
--     columns, V1_23 run after) and that limitation is not re-litigated
--     here. This file instead performs expand, backfill, assert, and
--     contract for schedule.id in one maintenance-window cutover — a single
--     Flyway migration, not a multi-deploy rollout — because this is the
--     only column still on the old shape and the project has no production
--     traffic to protect mid-migration (ADR-028 decision drivers, O-02).
--
-- Source column width note: unlike the inventory's VARCHAR(128) surfaces,
-- `schedule.id` is VARCHAR(255). The backfill expression
-- (`UNHEX(REPLACE(id, '-', ''))`) and byte order (D-12 — no `UUID_TO_BIN`
-- swap, same byte order as shared-module's `UuidBinaryCodec.encode`) are
-- unaffected by the source column's declared width; both only depend on the
-- value being a canonical 36-character hyphenated UUID string, which this
-- column always holds (it is assigned from `restaurant.identifier`, itself a
-- `UuidGenerator.generate()` value — see `CreateScheduleAdapter`).
--
-- Idempotency / re-run safety (D-05) — a SINGLE short-circuit at the top of
-- this file, not per-statement guards alone:
--   V1_22/V1_23 compose many per-statement guards (sp_add_column_if_absent,
--   sp_drop_column_if_exists, etc.) across TWO files run in two separate
--   deploys. That is safe there because nothing downstream of the rename in
--   V1_23 ever re-runs V1_22's ADD COLUMN step again in the same file.
--   Here, expand -> backfill -> assert -> contract all live in ONE file, so
--   a second full run after a first full success would reach, in order:
--     1. `sp_add_column_if_absent` re-adding `id_bin` (missing again because
--        the first run already renamed it to `id`);
--     2. `sp_backfill_uuid_binary`'s guard `WHERE id_bin IS NULL AND id IS
--        NOT NULL` evaluating true for every row (the brand-new `id_bin` is
--        NULL for all of them);
--     3. `UNHEX(REPLACE(<already-binary id>, '-', ''))` reading the *binary*
--        `id` column as if it were still the hyphenated text form — not a
--        hyphenated UUID string, so the result is NULL for every row;
--     4. `sp_assert_backfill_complete` then hard-failing with "remaining >
--        0", correctly refusing to proceed, but blocking every subsequent
--        `flywayMigrate` run of an already-converted database for no reason.
--   Per-statement guards alone do not prevent step 1-3 from running — they
--   only prevent the individual ADD COLUMN / DROP COLUMN / RENAME COLUMN
--   calls from erroring out; the backfill guard's own WHERE clause is what
--   goes wrong. The single short-circuit avoids reaching any of that:
--   everything below runs inside `sp_migrate_schedule_id_binary`, which
--   checks `schedule.id`'s current `DATA_TYPE` first and returns
--   immediately if it is already `'binary'` — the ENTIRE body, not one
--   statement, is skipped on a second run.
--
-- Batch size: 5000 rows per UPDATE (same as V1_22 — develop:
-- deprecation-and-migration standing guidance; this environment has no
-- production traffic, ADR-028 decision drivers O-02, so 5000 is a
-- conservative default, not a measured figure).
--
-- No `ADD COLUMN IF NOT EXISTS` / `DROP COLUMN IF EXISTS` / `DROP INDEX IF
-- EXISTS` syntax appears anywhere below — MySQL has none of these at any
-- version (ERROR 1064 — confirmed in V1_22/V1_23's own headers and this
-- project's runbook, §3). `DROP PROCEDURE IF EXISTS` is the one IF-EXISTS
-- form MySQL actually supports, used for every helper procedure below.
--
-- No FOREIGN KEY, INDEX, or UNIQUE KEY other than the PRIMARY KEY covers
-- `schedule.id` today, so there is no index drop/recreate step here (unlike
-- V1_23's restaurant_tags/menu/timetable/etc. cases).
--
-- Minimum version: MySQL >= 8.0 (information_schema.COLUMNS/
-- TABLE_CONSTRAINTS, stored procedures with dynamic SQL via PREPARE/EXECUTE
-- — no version-gated DDL syntax is used anywhere in this file). Same
-- container this project already verified V1_22/V1_23 against.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Step 0: helper procedures — same shapes as V1_22 (sp_add_column_if_absent,
-- sp_backfill_uuid_binary) and V1_23 (sp_assert_backfill_complete,
-- sp_drop_pk_if_exists, sp_add_pk_if_absent, sp_rename_column_if_exists,
-- sp_drop_column_if_exists). V1_22/V1_23 each drop their own copies of these
-- at the end of their own files (D-05 — "scratch infrastructure for this
-- migration only"), so they are not reusable across files and are redefined
-- here rather than invented from scratch.
-- ----------------------------------------------------------------------------

DROP PROCEDURE IF EXISTS prototype_reservation.sp_add_column_if_absent;

CREATE PROCEDURE prototype_reservation.sp_add_column_if_absent(
    IN p_schema VARCHAR(64),
    IN p_table VARCHAR(64),
    IN p_col VARCHAR(64),
    IN p_coldef VARCHAR(512),
    IN p_after VARCHAR(64)
)
BEGIN
    DECLARE v_exists INT;
    SELECT COUNT(*) INTO v_exists
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = p_schema
       AND TABLE_NAME = p_table
       AND COLUMN_NAME = p_col;
    IF v_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE `', p_schema, '`.`', p_table, '` ADD COLUMN `', p_col, '` ', p_coldef,
            IF(p_after IS NOT NULL AND p_after <> '', CONCAT(' AFTER `', p_after, '`'), '')
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END;

DROP PROCEDURE IF EXISTS prototype_reservation.sp_backfill_uuid_binary;

CREATE PROCEDURE prototype_reservation.sp_backfill_uuid_binary(
    IN p_schema VARCHAR(64),
    IN p_table VARCHAR(64),
    IN p_src_col VARCHAR(64),
    IN p_dst_col VARCHAR(64),
    IN p_batch_size INT
)
BEGIN
    DECLARE v_affected INT DEFAULT 1;
    WHILE v_affected > 0 DO
        SET @sql = CONCAT(
            'UPDATE `', p_schema, '`.`', p_table, '`',
            ' SET `', p_dst_col, '` = UNHEX(REPLACE(`', p_src_col, '`, ''-'', ''''))',
            ' WHERE `', p_dst_col, '` IS NULL AND `', p_src_col, '` IS NOT NULL',
            ' LIMIT ', p_batch_size
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET v_affected = ROW_COUNT();
    END WHILE;
END;

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
                SET MESSAGE_TEXT = 'binary-pk contract blocked: backfill incomplete for this column — re-run the backfill step before contracting';
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

-- Same DATA_TYPE-guarded shape as V1_23's copy — see that file's own comment
-- for why a plain name-existence check is not enough (the column is renamed
-- back to its original name after a successful run).
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

-- ----------------------------------------------------------------------------
-- Step 1: the single short-circuit (see header). Everything that touches
-- `schedule.id` lives inside this one procedure, guarded by `schedule.id`'s
-- current DATA_TYPE, so a second run of this file is a no-op from the first
-- statement of the body onward.
-- ----------------------------------------------------------------------------

DROP PROCEDURE IF EXISTS prototype_reservation.sp_migrate_schedule_id_binary;

CREATE PROCEDURE prototype_reservation.sp_migrate_schedule_id_binary()
BEGIN
    DECLARE v_current_type VARCHAR(64);

    SELECT DATA_TYPE INTO v_current_type
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'prototype_reservation'
       AND TABLE_NAME = 'schedule'
       AND COLUMN_NAME = 'id';

    IF v_current_type IS NULL OR v_current_type <> 'binary' THEN
        -- Expand: add the BINARY(16) sibling.
        CALL prototype_reservation.sp_add_column_if_absent(
            'prototype_reservation', 'schedule', 'id_bin',
            'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼, restaurant.id 사본)''',
            'id'
        );

        -- Backfill: batched, idempotent, UNHEX(REPLACE(..., '-', '')) — same
        -- byte order as shared-module's UuidBinaryCodec.encode (D-12).
        CALL prototype_reservation.sp_backfill_uuid_binary(
            'prototype_reservation', 'schedule', 'id', 'id_bin', 5000
        );

        -- Assert: refuse to contract if any row was left unconverted.
        CALL prototype_reservation.sp_assert_backfill_complete(
            'prototype_reservation', 'schedule', 'id', 'id_bin'
        );

        -- Contract: drop PK, drop the old VARCHAR(255) column, rename the
        -- BINARY(16) sibling into its place, finalize NOT NULL, recreate PK.
        CALL prototype_reservation.sp_drop_pk_if_exists('prototype_reservation', 'schedule');
        CALL prototype_reservation.sp_drop_column_if_exists('prototype_reservation', 'schedule', 'id');
        CALL prototype_reservation.sp_rename_column_if_exists('prototype_reservation', 'schedule', 'id_bin', 'id');

        ALTER TABLE prototype_reservation.schedule
            MODIFY COLUMN id BINARY(16) NOT NULL COMMENT '식별키 (restaurant.id 사본)';

        CALL prototype_reservation.sp_add_pk_if_absent('prototype_reservation', 'schedule', 'id');
    END IF;
END;

CALL prototype_reservation.sp_migrate_schedule_id_binary();

-- ----------------------------------------------------------------------------
-- Step 2: drop every helper procedure. Scratch infrastructure for this
-- migration only (same reasoning as V1_22/V1_23's own teardown) — never left
-- behind as schema debt.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS prototype_reservation.sp_migrate_schedule_id_binary;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_add_column_if_absent;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_backfill_uuid_binary;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_assert_backfill_complete;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_drop_pk_if_exists;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_add_pk_if_absent;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_rename_column_if_exists;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_drop_column_if_exists;
