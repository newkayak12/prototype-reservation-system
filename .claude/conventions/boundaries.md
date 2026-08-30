# boundaries — what must not be touched casually

Plan/SetGoal treat these as hard constraints; violations fail the QualityGate.

## Do not modify without explicit user approval
- Applied Flyway migrations under `adapter-module/**/db/migration/**` — append-only,
  never edit a migration that has already run against any environment.
- ADR files under `docs/v2/adr/**` once their status is `Accepted` — immutable per
  MADR convention; a reversal is a new superseding ADR, not an edit.
- `main` branch — protected, no direct pushes.

## Off-limits entirely
- Generated build output (`build/`, `.gradle/`, QueryDSL generated `Q*` classes).
- Vendored/local tooling state ignored by git (`.claude/v1/**` non-harness docs, prior
  harness cycle state) — not source of truth, do not resurrect or reference as current.

## Requires a dependent update when changed
- Changing the module dependency direction (e.g. `core-module` gaining an external
  dependency) requires updating the "Module Dependencies" section in the project's
  `CLAUDE.md`.
- A significant architectural decision requires a new or superseding ADR under
  `docs/v2/adr/` before or alongside the code change (see `docs/v2/adr/ADR-INDEX.md`).
