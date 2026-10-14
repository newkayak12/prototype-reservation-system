# Situational — Data Discipline

**Trigger**: DB schema design, migration, backup, retention-period decisions, or adding a sensitive column

## D-01: PII classification — tag at column level

- **Why**: Without a *list* of which columns are personal data, GDPR / K-Personal Information Protection Act compliance is impossible.
- **How**:
  - When adding a new column, tag `PII: Y/N`, `Sensitivity: low/medium/high`, `Purpose: <why collected>`
  - Consider splitting PII columns into a *separate table* (isolated access = reduced exposure surface)
  - Linked to security rule [`security.md#s-07`](./security.md)

## D-02: Retention policy — enforce *in code*

- **Why**: "Delete when no longer needed" is *never kept*. A policy not automated is not a policy.
- **How**:
  - Specify retention period per entity (e.g. User = 30 days after withdrawal, logs = 90 days, payments = 5 years)
  - *Auto-delete or anonymize* via expiry cron job or TTL index (NoSQL)
  - Delete vs anonymize decision: anonymize if needed for analytics, otherwise hard delete

## D-03: Backup tested — an untested restore is no backup at all

- **Why**: "Backup exists" and "backup restores" are different problems. A common pattern in production incidents.
- **How**:
  - Decide backup frequency, retention, and encryption policy (RPO/RTO)
  - **Quarterly restore drill** — restore to actual staging and verify row counts and checksums
  - Document the restore procedure as a runbook

## D-04: Expand / Contract migration — zero downtime

- **Why**: Changing schema in one step *breaks consistency during deployment*. Multi-phase is required for live operation.
- **How**:
  - **Expand**: add new columns/tables (NULL-allowed or with defaults). Old code unchanged.
  - **Migrate**: *write to both* old and new simultaneously. Backfill old data.
  - **Contract**: remove old code → drop old columns/tables
  - *Deploy + verify* at each step. Never combine steps in one PR.

## D-05: Migration scripts are idempotent

- **Why**: Re-running a partially applied migration after failure is common. Non-idempotent scripts break the system.
- **How**:
  - `CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... IF NOT EXISTS`, `INSERT ... ON CONFLICT DO NOTHING`
  - Backfill scripts are *safe to re-run* (where clause processes only unhandled rows)
  - Use the migration tool's transactional support where available
  - **MySQL caveat**: `ALTER TABLE ... ADD/DROP COLUMN IF [NOT] EXISTS` and `ADD/DROP INDEX IF [NOT] EXISTS` are NOT portable — check the target engine before using them. PostgreSQL and MariaDB support this syntax; **MySQL never has, at any version** (confirmed directly against this project's own `mysql:latest` container while building V1_22/V1_23 — every one of `ADD COLUMN IF NOT EXISTS`, `DROP COLUMN IF EXISTS`, `ADD INDEX IF NOT EXISTS`, `ADD UNIQUE KEY IF NOT EXISTS`, `DROP INDEX IF EXISTS` is `ERROR 1064`, a hard syntax error). An earlier draft of this convention cited "MySQL >= 8.0.29" for this syntax — that citation was fabricated; 8.0.29 added INSTANT `ADD COLUMN` in any position, an unrelated feature. **On MySQL**, make each DDL statement idempotent with a guard procedure that checks `information_schema.COLUMNS` / `information_schema.STATISTICS` / `information_schema.TABLE_CONSTRAINTS` first and only runs the `ALTER TABLE` (via `PREPARE`/`EXECUTE` dynamic SQL) when the target is actually missing/present — see `V1_22__binary_pk_identifier_expand.sql` and `V1_23__binary_pk_identifier_contract.sql` for the pattern (`sp_add_column_if_absent`, `sp_drop_column_if_exists`, `sp_add_index_if_absent`, `sp_drop_index_if_exists`, `sp_drop_pk_if_exists`/`sp_add_pk_if_absent`, `sp_rename_column_if_exists`).
  - **Rename-back guard**: a guard that only checks "does a column/index with this name exist" is not enough when a step **renames** something back to its original name (e.g. an expand/contract swap): after a full successful run, the final column exists under the pre-migration name too, so a naive existence check would let a second run drop it again. Guard the destructive step on a property that actually distinguishes "not yet migrated" from "already migrated" (e.g. `DATA_TYPE` for a type-changing swap), not merely on name existence.

## D-06: Schema changes are backward-compatible first

- **Why**: Clients and other services are *not updated simultaneously*. An incompatible change forces *simultaneous deployment*.
- **How**:
  - Column *addition* is safe. Column *removal* is deferred until the contract step.
  - Column *rename* → 3 steps: add new column + write to both + remove old column.
  - Same principle for API responses. Field *addition* OK; *removal* requires a deprecation period.

## D-07: Foreign keys + Indexes — explicit

- **Why**: Relationships expressed only in code (without FKs) produce *orphan data*. FKs without indexes wreck join performance.
- **How**:
  - If a relationship exists, add FK constraints *by default*. Remove only intentionally when there is a performance issue.
  - *Auto-create index on FK columns* (varies by DBMS — verify)

## D-08: Start normalized — denormalize only with *evidence*

- **Why**: Normalization *guarantees consistency*. Denormalization is a trade against *consistency risk*.
- **How**:
  - Start at 3NF
  - Denormalize only when a measured bottleneck exists
  - Record denormalization decisions in an ADR — *which query, which measurement, which trade-off*

## D-09: Soft delete vs Hard delete — decide explicitly

- **Why**: Both are valid. But mixing them *without an explicit decision* breaks data consistency.
- **How**:
  - Decide per entity (User = soft, ephemeral log = hard)
  - If soft delete is adopted: enforce `WHERE deleted_at IS NULL` *in every query* — omitting it causes *data leakage* or *ghost data*
  - GDPR right-to-be-forgotten requires *hard delete or anonymization*

## D-10: Time zone — store UTC, convert to local only for display

- **Why**: Mixed time zones cause *silent data corruption*. Extremely difficult to debug.
- **How**:
  - DB, logs, messages = UTC + ISO 8601
  - Convert to user time zone only in UI and reports
  - Use timezone-aware types (`TIMESTAMPTZ`, etc.) for any new time column

## D-11: Check data residency requirements

- **Why**: GDPR (EU), K-Personal Information Protection Act, China Cybersecurity Law, etc. may require *per-country storage locations*.
- **How**:
  - If user regions are likely to diversify, review *region-aware data storage* design in advance
  - Record legal requirements as an ADR when selecting a cloud region

## D-12: Identifier storage — BINARY(16) in the DB, string at every other boundary

- **Why**: Storing a UUID identifier as `VARCHAR` text wastes space and makes every PK/FK comparison a string comparison. But rewriting every layer to pass `UUID`/`byte[]` around spreads a storage decision into domain and API code that should never need to know about it. See [[ADR-028-binary-pk-identifier-storage]] for the full argument (mechanism choice, byte order, expand/contract path).
- **How**:
  - **Storage form**: UUID-bearing identifier columns (primary keys and the columns/association join columns that reference them) are `BINARY(16)`, holding the 16 bytes of the UUID in the text's own hex order (no `UUID_TO_BIN(x, 1)`-style swap) — see ADR-028 for why no swap applies to this project's UUIDv7 identifiers.
  - **Who owns the conversion**: the persistence boundary only, via a single `AttributeConverter<String, ByteArray>` (or, if `@Convert` is not honored on an `@Id`/`@EmbeddedId` member, the designated fallback: per-entity `@Convert` redeclaration or a Hibernate `UserType`/`BasicValueConverter` — ADR-028 §결정 3). Never hand-roll the byte conversion at a call site; delegate to the shared codec.
  - **Canonical form elsewhere**: domain, application, and API layers keep the identifier as a lowercase, hyphenated UUID string (the `Persistable<String>` / `UuidGenerator.generate(): String` contract is unchanged). Nothing above the persistence boundary ever sees a `ByteArray` or a raw binary column.
  - **Out of scope**: business-meaningful strings that happen to live in an identifier-shaped column are not UUIDs and are not converted — e.g. a user's chosen login id (`user.login_id`), or a nonce-based ticket code (`waiting_queue.ticket_id`, a `SecureRandom` hex string, not a `UuidGenerator` value). Confirm with the writer-tracing method in [[binary-pk-identifier-inventory]] before assuming a column is a UUID.

## Related skills

- `develop:database-workflow` — DB work entry point
- `develop:database-optimizer` — infrastructure and operations-side DB tuning
- `develop:sql-pro` — query writing and tuning
- `develop:transaction-boundary-reviewer` — transaction and consistency boundaries
