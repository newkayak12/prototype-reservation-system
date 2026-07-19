# verification — what "verified" means in this project

The harness Test stage executes these commands as deterministic evidence; the
QualityGate weighs their output over any narrative. Commands must run non-interactively
from the project root.

## Commands
- Build: `./gradlew build -x test`
- Test (full): `./gradlew test`
- Test (single module): `./gradlew :adapter-module:test` (swap module — `application-module`, `core-module`)
- Test (single class): `./gradlew test --tests <ClassName>`
- Coverage: `./gradlew test jacocoTestReport`
- Lint/format check: `./gradlew spotlessCheck`
- Static analysis: `./gradlew detekt`
- All quality checks: `./gradlew check`

## Pass bar
- A change is verified only when the commands above pass AND the changed behavior was
  exercised end-to-end at least once.
- `detekt` runs with `maxIssues: 0` — any finding fails the bar, no threshold to squeak under.
- Test coverage does not regress below the enforced Jacoco threshold.
- No direct pushes to `main` — verification happens on a feature branch before PR/merge.
