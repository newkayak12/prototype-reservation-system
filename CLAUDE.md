# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Prototype Reservation System** built with **Spring Boot 3.4.5** and **Kotlin 2.0.10** following **Hexagonal Architecture** and **Domain-Driven Design** principles. The project demonstrates advanced software architecture patterns with strict code quality enforcement.

## Architecture

### Module Structure (Hexagonal Architecture)
- **`adapter-module`**: Controllers, Security, Persistence, JPA Entities (Infrastructure Layer)
- **`application-module`**: Use Cases, Input/Output Ports (Application Layer)  
- **`core-module`**: Domain Entities, Domain Services (Domain Layer)
- **`shared-module`**: Enumerations, Abstract Exceptions, Utilities (Shared Kernel)
- **`test-module`**: Test utilities and common fixtures

### Key Architectural Patterns
- **Hexagonal Architecture**: Clear separation between domain, application, and infrastructure
- **Domain-Driven Design**: Rich domain models with domain services
- **Clean Architecture**: Dependency inversion with ports and adapters
- **CQRS patterns**: Separate read/write operations where applicable

## Common Development Commands

### Build & Test
```bash
# Build entire project
./gradlew build

# Run tests
./gradlew test

# Run tests with coverage report
./gradlew test jacocoTestReport

# Run specific module tests
./gradlew :adapter-module:test
./gradlew :application-module:test
./gradlew :core-module:test
```

### Code Quality
```bash
# Apply code formatting (Spotless)
./gradlew spotlessApply

# Check code formatting
./gradlew spotlessCheck

# Run static analysis (Detekt)
./gradlew detekt

# Run all quality checks
./gradlew check

# Pre-commit hook (runs spotless + detekt + git add)
./gradlew gitPreCommitHook
```

### Application Execution
```bash
# Run with Docker Compose (MySQL + Redis)
docker-compose up -d
./gradlew :adapter-module:bootRun

# Run only the application (requires manual DB setup)
./gradlew :adapter-module:bootRun
```

### Database Management
```bash
# Run Flyway migrations
./gradlew :adapter-module:flywayMigrate

# Clean and recreate database
./gradlew :adapter-module:flywayClean :adapter-module:flywayMigrate
```

## Technology Stack

### Core Framework
- **Spring Boot 3.4.5** with Spring Security, JPA, Validation
- **Kotlin 2.0.10** with coroutines support
- **MySQL** with Flyway migrations
- **Redis** for caching and session management
- **QueryDSL** for type-safe database queries

### Testing Framework
- **Layer-specific testing strategy**:
  - **Adapter Layer**: Kotest + MockK + Testcontainers (MySQL/Redis)
  - **Application Layer**: JUnit + MockK + AssertJ
  - **Core Layer**: Kotest for domain logic testing
- **Fixture Monkey** for property-based testing and edge cases
- **Spring REST Docs** for API documentation

### Code Quality Tools
- **Spotless** with Ktlint for consistent formatting
- **Detekt** for static code analysis
- **Jacoco** for test coverage reporting
- **SonarQube** and **CodeCov** integration

## Development Guidelines

### Code Organization
```kotlin
// Domain structure example
com.reservation.{domain}
├── {Domain}.kt                 // Aggregate root
├── service/
│   └── {Domain}DomainService.kt
├── vo/                        // Value objects
├── policy/                    // Domain policies
└── snapshot/                  // Domain snapshots
```

### Testing Strategy
- **Slice Testing**: Test each layer independently with mocking
- **BDD Style**: Use descriptive test names and Given-When-Then structure
- **Property-Based Testing**: Use Fixture Monkey for edge case validation
- **Integration Testing**: Use Testcontainers for real database testing

### Security Implementation
- **JWT-based authentication** with refresh tokens
- **XSS protection** with request wrapper filtering
- **Role-based authorization** (USER, SELLER, ADMIN)
- **Encrypted sensitive data** storage

### Database Patterns
- **Time-based UUID** primary keys for distributed system readiness
- **Soft delete** with logical delete flags
- **Audit fields** (created_at, updated_at) on all entities
- **Flyway migrations** for database versioning

## Key Patterns & Conventions

### Domain Modeling
- Rich domain objects with behavior, not anemic data containers
- Domain services for complex business logic spanning multiple aggregates
- Value objects for data that should be immutable and validated
- Repository pattern with ports/adapters for data access

### Error Handling
- Custom domain exceptions inheriting from abstract base exceptions
- Global exception handling with `@ControllerAdvice`
- Meaningful error messages with internationalization support

### Configuration Management
- Environment-specific YAML configurations (`local`, `stage`, `production`)
- Property-based configuration with type-safe binding
- Docker Compose for local development environment

## Important Notes

### Quality Standards
- **Zero tolerance** for code quality violations (maxIssues: 0 in detekt)
- **Pre-commit hooks** automatically format and validate code
- **Test coverage** is enforced and reported to external services
- **Main branch protection** - no direct pushes allowed

### Module Dependencies
- `core-module` has NO external dependencies (pure domain logic)
- `application-module` depends only on `core-module` 
- `adapter-module` orchestrates and depends on `application-module`
- `shared-module` provides utilities but avoids becoming a "god module"

### Development Workflow
1. Create feature branch from main
2. Implement with TDD approach (test first)
3. Use pre-commit hooks for formatting/validation
4. All tests must pass before PR
5. Code review required before merge