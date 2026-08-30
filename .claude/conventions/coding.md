# coding — how code in this project is written

The harness engine reads this file: SetGoal folds it into acceptance criteria,
Implement executors follow it. Keep every rule concrete and checkable.

## Naming & structure
- Hexagonal module boundaries are physical, not just conventional:
  `core-module` (domain entities, domain services — pure, zero external deps) →
  `application-module` (use cases, input/output ports; depends only on `core-module`) →
  `adapter-module` (controllers, security, JPA persistence; orchestrates and depends on
  `application-module`) · `shared-module` (enums, abstract exceptions, utilities — kept
  thin, never a dependency dumping ground) · `test-module` (fixtures, test utilities).
- Domain package layout: `com.reservation.{domain}` with `service/` (domain services),
  `vo/` (value objects), `policy/` (domain policies), `snapshot/` (snapshots).
- Domain aggregates are rich, not anemic: behavior lives on the entity, not scattered
  across `*DomainService` classes that just orchestrate CRUD.

## Style
- Match the surrounding file's idiom before any general rule.
- Comments state constraints the code can't show — never narrate the change itself.
- Formatting is Spotless + Ktlint; static analysis is Detekt. `detekt.maxIssues: 0` —
  zero tolerance, no exceptions carved out ad hoc.

## Dependencies
- `core-module` must have zero external (framework/JPA/Spring) dependencies — this is
  enforced by the Gradle dependency graph, not just convention.
- No new runtime dependency without an ADR under `docs/v2/adr/` recording the decision.
