# Project Conventions

This document outlines the main conventions to follow when working on this project.

## Project Structure

This is a multi-module Gradle project with the following modules:

- `shared-module`: Contains shared code used across different modules.
- `core-module`: Contains the core domain logic of the application.
- `application-module`: Contains the application services and use cases.
- `adapter-module`: Contains the adapters for external interfaces, such as the web layer and persistence layer.
- `test-module`: Contains test-related utilities and configurations.

## Build and Dependencies

- The project is built with Gradle.
- It uses Spring Boot 3.4.5 and Kotlin 2.0.10.
- The Java version is 21.
- Key dependencies include:
    - Spring Data JPA for database access.
    - Spring Security for authentication and authorization.
    - Flyway for database migrations.
    - QueryDSL for type-safe database queries.
    - Testcontainers for integration tests.

## Coding Style

- **Formatting:**
    - Use UTF-8 charset.
    - Use LF for line endings.
    - Insert a final newline at the end of files.
    - Trim trailing whitespace.
    - Use 4 spaces for indentation.
    - Maximum line length is 100 characters.
- **Kotlin:**
    - Follow the official Kotlin code style.
    - Use trailing commas.
    - Wildcard imports are allowed.
- **Static Analysis:**
    - `detekt` is used for static analysis. The configuration is in `detekt.yaml`.
    - `spotless` is used for code formatting. The configuration is in `build.gradle.kts`.

## Testing

- The project uses JUnit 5 and Kotest for writing tests.
- MockK and Fixture Monkey are used for creating mocks and test data.
- Jacoco is used for measuring code coverage.

## Git Workflow

- A pre-commit hook is set up to run `spotless` and `detekt` before each commit. This ensures that all committed code adheres to the coding style and quality standards.
