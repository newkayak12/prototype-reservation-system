-- ============================================================================
-- V1_22 — Binary PK identifier conversion: EXPAND step (D-04 phase 1 of 2)
-- ============================================================================
-- Scope: docs/v2/adr/ADR-028-binary-pk-identifier-storage.md (결정 #5),
--        .claude/conventions/data.md#D-12, docs/v2/analysis/binary-pk-identifier-inventory.md
--
-- What this file does, and nothing else:
--   For every column the inventory marked CONVERT (49 surfaces / 41 distinct
--   physical columns across 20 tables — PK `id` columns + the explicit and
--   implicit UUID-bearing columns), add a sibling `<col>_bin BINARY(16) NULL`
--   column and backfill it from the existing VARCHAR(128) text using
--   UNHEX(REPLACE(col, '-', '')) — the same byte order as
--   shared-module's UuidBinaryCodec.encode (no UUID_TO_BIN swap; see ADR-028
--   "바이트 순서 근거").
--
-- What this file deliberately does NOT do:
--   - It does not touch any PRIMARY KEY, INDEX, or UNIQUE KEY.
--   - It does not drop or rename any existing column.
--   - It does not change application/entity mapping — old columns stay the
--     columns Hibernate reads/writes until the "migrate" (app-deploy, no DDL)
--     phase that ADR-028 describes between this file and the contract file.
--   - It does not touch `active_marker` (generated column on
--     timetable_occupancy, V1_21__timetable_occupancy_hold.sql:22-24) — that
--     column depends only on released_at, not on any identifier.
--   - It does not touch `waiting_queue.ticket_id` (nonce, not a UUID —
--     inventory §2d) or `user_change_history.user_id` (login id, not a UUID —
--     inventory §2a item 4) or any other LEAVE-AS-VARCHAR surface.
--   - It does not touch the `schedule` table. `schedule.id` stores a copy of
--     `restaurant.id` (ScheduleEntity.kt:20-22, `@Id @Column(name="id") val
--     restaurantId: String`) but is not a TimeBasedPrimaryKey subclass and was
--     not enumerated in binary-pk-identifier-inventory.md's 53-surface sweep.
--     Converting it is out of this subgoal's scope (bounded by U1's
--     inventory) — recorded here and in the runbook as a gap for the
--     inventory/ADR owner to close, not decided unilaterally by this file.
--
-- Idempotency / re-run safety (D-05) — this migration WILL be re-run after a
-- partial failure:
--   - MySQL (unlike MariaDB) has NO `ADD COLUMN IF NOT EXISTS` syntax at all
--     (confirmed against this project's own mysql:latest container —
--     `ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...` is ERROR 1064, a hard
--     syntax error, not a feature gated behind any version). An earlier
--     version of this file assumed MariaDB's `IF [NOT] EXISTS` extension was
--     portable MySQL >= 8.0.29 syntax; it is not — that claim was wrong and
--     is corrected here. Every `ADD COLUMN` instead goes through
--     `sp_add_column_if_absent`, a guard procedure that checks
--     information_schema.COLUMNS first and skips the DDL if the column is
--     already there (added by a prior, interrupted attempt).
--   - Backfill never runs as one UPDATE over the whole table. It runs through
--     `sp_backfill_uuid_binary`, which loops `UPDATE ... LIMIT <batch>` until
--     a pass affects 0 rows. Each batch's WHERE clause
--     (`dst IS NULL AND src IS NOT NULL`) makes every batch — and the whole
--     backfill — a no-op on rows already converted, so resuming mid-table
--     after an interruption reprocesses nothing it already finished.
--   - Both helper procedures are dropped and recreated
--     (`DROP PROCEDURE IF EXISTS` then `CREATE PROCEDURE`), so re-running
--     this file from the top after any failure point is always safe.
--
-- Minimum version: MySQL >= 8.0 (information_schema.COLUMNS, stored
-- procedures with dynamic SQL via PREPARE/EXECUTE — no version-gated DDL
-- syntax is used anywhere in this file). The project's compose.yaml pins
-- `mysql:latest`; verified directly against that image (server reports
-- `26.7.0`, `MySQL Community Server - GPL`) in this worktree.
--
-- Batch size: 5000 rows per UPDATE (develop:deprecation-and-migration
-- standing guidance) — this environment has no production traffic (ADR-028
-- decision drivers, O-02), so 5000 is a conservative default, not a measured
-- figure.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Step 0: backfill helper — batched, idempotent UPDATE loop driven by two
-- column names and a schema/table pair supplied at CALL time. One procedure,
-- reused for all 41 columns below, instead of 41 hand-copied UPDATE loops.
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- Step 0b: ADD-COLUMN guard — MySQL has no `ADD COLUMN IF NOT EXISTS`
-- (that is a MariaDB-only extension; see header note above). This procedure
-- reproduces the same re-run safety using information_schema.COLUMNS, the
-- same pattern V1_23 already uses for PRIMARY KEY and RENAME COLUMN guards.
-- `p_after` may be NULL/'' to add the column at the end instead of AFTER a
-- named column (not needed below, but kept general).
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

-- ----------------------------------------------------------------------------
-- Step 1: ADD COLUMN — one `*_bin BINARY(16) NULL` sibling per CONVERT
-- surface, placed right after its source column. All tables are schema-
-- qualified `prototype_reservation.<table>` (the DDL target schema — see the
-- runbook's note on the flyway/prototype_reservation schema split).
-- ----------------------------------------------------------------------------

-- user (inventory §1 #13) — PK
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'user', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');

-- user_change_history (inventory §1 #12 PK, §2a #3 user_uuid)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'user_change_history', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');

-- PII: user_uuid references user.id (inventory §2a #3). D-01/S-07 require
-- PII/Sensitivity/Purpose tagging on any new column on this audit table.
--
-- Why reversibility matters more here than on an ordinary table: this table
-- IS the history, not a cache of current state. An ordinary table's current
-- row can be recomputed or re-read from upstream if a backfill goes wrong;
-- a change-history row cannot — once the original VARCHAR text is dropped
-- (contract step), a wrong backfill is permanent, undetectable data loss in
-- the one place whose entire purpose is being evidence. That is why this
-- file only ever ADDs a column here and never drops or rewrites user_uuid
-- itself — the contract file is the one that must re-verify backfill
-- completeness (sp_assert_backfill_complete) before it is allowed to drop
-- the original column on this table.
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'user_change_history', 'user_uuid_bin', 'BINARY(16) NULL COMMENT ''PII:Y Sensitivity:high Purpose:사용자 식별자 참조(변경 이력, user.id) — BINARY16 전환 중간 컬럼 (data.md D-01, security.md S-07)''', 'user_uuid');

-- user_access_history (inventory §1 #11 PK, §2a #2 user_uuid)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'user_access_history', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');

-- PII: same reasoning as user_change_history.user_uuid above — this table is
-- an access-history audit log, not current state.
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'user_access_history', 'user_uuid_bin', 'BINARY(16) NULL COMMENT ''PII:Y Sensitivity:high Purpose:사용자 식별자 참조(접근 이력, user.id) — BINARY16 전환 중간 컬럼 (data.md D-01, security.md S-07)''', 'user_uuid');

-- withdrawal_user (inventory §1 #9) — PK only; login_id stays VARCHAR (§2b #6)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'withdrawal_user', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');

-- company (inventory §1 #18) — PK only
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'company', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');

-- restaurant (inventory §1 #1 PK, §2c #7 company_id, §2c #8 user_id)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant', 'company_id_bin', 'BINARY(16) NULL COMMENT ''company 식별키 (BINARY16 전환 중간 컬럼)''', 'company_id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant', 'user_id_bin', 'BINARY(16) NULL COMMENT ''user 식별키 (BINARY16 전환 중간 컬럼)''', 'user_id');

-- restaurant_photo (inventory §1 #5 PK, §3a #3 restaurant_id implicit join col)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant_photo', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant_photo', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''음식점 식별키 (BINARY16 전환 중간 컬럼)''', 'restaurant_id');

-- restaurant_working_day (inventory §2a #1 / §3a #1 — restaurant_id is half
-- of the composite PK (restaurant_id, day); `day` is an ENUM, not converted)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant_working_day', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''음식점 식별키 (BINARY16 전환 중간 컬럼)''', 'restaurant_id');

-- restaurant_tags (inventory §1 #2 PK, §3a #2 restaurant_id implicit join col)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant_tags', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant_tags', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''음식점 식별키 (BINARY16 전환 중간 컬럼)''', 'restaurant_id');

-- restaurant_nationalities (inventory §1 #4 PK, §3a #4 restaurant_id implicit join col)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant_nationalities', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant_nationalities', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''음식점 식별키 (BINARY16 전환 중간 컬럼)''', 'restaurant_id');

-- restaurant_cuisines (inventory §1 #3 PK, §3a #5 restaurant_id implicit join col)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant_cuisines', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'restaurant_cuisines', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''음식점 식별키 (BINARY16 전환 중간 컬럼)''', 'restaurant_id');

-- menu (inventory §1 #14 PK, §2c #10 restaurant_id)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'menu', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'menu', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''음식점 식별키 (BINARY16 전환 중간 컬럼)''', 'restaurant_id');

-- menu_photo (inventory §1 #15 PK, §3a #6 menu_id implicit join col)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'menu_photo', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'menu_photo', 'menu_id_bin', 'BINARY(16) NULL COMMENT ''메뉴 식별키 (BINARY16 전환 중간 컬럼)''', 'menu_id');

-- time_span (inventory §1 #8 PK, §2c #13 restaurant_id NOT NULL)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'time_span', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'time_span', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''음식점 식별키 (BINARY16 전환 중간 컬럼)''', 'restaurant_id');

-- holiday (inventory §1 #6 PK, §2c #11 restaurant_id NOT NULL)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'holiday', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'holiday', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''음식점 식별키 (BINARY16 전환 중간 컬럼)''', 'restaurant_id');

-- table (inventory §1 #7 PK, §2c #12 restaurant_id NOT NULL) — reserved word, backtick-quoted
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'table', 'id_bin', 'BINARY(16) NULL COMMENT ''식별키 (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'table', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''음식점 식별키 (BINARY16 전환 중간 컬럼)''', 'restaurant_id');

-- timetable (inventory §1 #17 PK, §2c #9 restaurant_id)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'timetable', 'id_bin', 'BINARY(16) NULL COMMENT ''시간표 ID (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'timetable', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''매장 ID (BINARY16 전환 중간 컬럼)''', 'restaurant_id');

-- timetable_occupancy (inventory §1 #16 PK, §3a #7 timetable_id implicit join
-- col, §2c #14 user_id). active_marker (V1_21) is NOT touched here or in the
-- contract file — it is a generated column depending only on released_at.
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'timetable_occupancy', 'id_bin', 'BINARY(16) NULL COMMENT ''시간표 점유 ID (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'timetable_occupancy', 'timetable_id_bin', 'BINARY(16) NULL COMMENT ''시간표 ID (BINARY16 전환 중간 컬럼)''', 'timetable_id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'timetable_occupancy', 'user_id_bin', 'BINARY(16) NULL COMMENT ''사용자 ID (BINARY16 전환 중간 컬럼)''', 'user_id');

-- reservation (inventory §1 #10 PK, §2e #18-21 user_id/restaurant_id/timetable_id/timetable_occupancy_id)
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'reservation', 'id_bin', 'BINARY(16) NULL COMMENT ''예약 PK (BINARY16 전환 중간 컬럼)''', 'id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'reservation', 'user_id_bin', 'BINARY(16) NULL COMMENT ''사용자 ID (BINARY16 전환 중간 컬럼)''', 'user_id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'reservation', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''매장 ID (BINARY16 전환 중간 컬럼)''', 'restaurant_id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'reservation', 'timetable_id_bin', 'BINARY(16) NULL COMMENT ''시간표 ID (BINARY16 전환 중간 컬럼)''', 'timetable_id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'reservation', 'timetable_occupancy_id_bin', 'BINARY(16) NULL COMMENT ''시간표 점유 ID (BINARY16 전환 중간 컬럼)''', 'timetable_occupancy_id');

-- waiting_queue (inventory §2d #15 restaurant_id, #16 user_id — both NOT
-- NULL). `id` (BIGINT AUTO_INCREMENT) and `ticket_id` (SecureRandom nonce,
-- not a UUID — §2d #17) are untouched.
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'waiting_queue', 'restaurant_id_bin', 'BINARY(16) NULL COMMENT ''매장 ID (BINARY16 전환 중간 컬럼)''', 'restaurant_id');
CALL prototype_reservation.sp_add_column_if_absent('prototype_reservation', 'waiting_queue', 'user_id_bin', 'BINARY(16) NULL COMMENT ''사용자 ID (BINARY16 전환 중간 컬럼)''', 'user_id');

-- ----------------------------------------------------------------------------
-- Step 2: backfill every `*_bin` column just added, batched 5000 rows at a
-- time, guarded so re-running any CALL after a crash only processes rows the
-- previous attempt had not reached yet.
-- ----------------------------------------------------------------------------
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'user', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'user_change_history', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'user_change_history', 'user_uuid', 'user_uuid_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'user_access_history', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'user_access_history', 'user_uuid', 'user_uuid_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'withdrawal_user', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'company', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant', 'company_id', 'company_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant', 'user_id', 'user_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant_photo', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant_photo', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant_working_day', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant_tags', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant_tags', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant_nationalities', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant_nationalities', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant_cuisines', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'restaurant_cuisines', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'menu', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'menu', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'menu_photo', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'menu_photo', 'menu_id', 'menu_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'time_span', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'time_span', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'holiday', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'holiday', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'table', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'table', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'timetable', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'timetable', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'timetable_occupancy', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'timetable_occupancy', 'timetable_id', 'timetable_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'timetable_occupancy', 'user_id', 'user_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'reservation', 'id', 'id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'reservation', 'user_id', 'user_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'reservation', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'reservation', 'timetable_id', 'timetable_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'reservation', 'timetable_occupancy_id', 'timetable_occupancy_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'waiting_queue', 'restaurant_id', 'restaurant_id_bin', 5000);
CALL prototype_reservation.sp_backfill_uuid_binary('prototype_reservation', 'waiting_queue', 'user_id', 'user_id_bin', 5000);

-- ----------------------------------------------------------------------------
-- Step 3: drop both helper procedures. They are scratch infrastructure for
-- this migration only — re-created from scratch (DROP IF EXISTS + CREATE) if
-- a future migration needs the same pattern, never left behind as schema
-- debt.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS prototype_reservation.sp_backfill_uuid_binary;
DROP PROCEDURE IF EXISTS prototype_reservation.sp_add_column_if_absent;
