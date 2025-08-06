# Code Style Conventions

This document outlines the coding style conventions to follow when working on this project.

## Architecture

The project follows a hexagonal architecture (ports and adapters) pattern. The code is organized into the following modules:

- `core-module`: Contains the core domain models and business logic. This module should not have any dependencies on other modules.
- `application-module`: Contains the application services (use cases) that orchestrate the domain logic. This module defines the ports (interfaces) for external dependencies.
- `adapter-module`: Contains the implementations of the ports defined in the application module. This includes the web layer (controllers), the persistence layer (repositories), and other external integrations.

## Naming Conventions

- **Classes:** Use `PascalCase` for class names (e.g., `CreateGeneralUserService`).
- **Functions and Variables:** Use `camelCase` for function and variable names (e.g., `createGeneralUser`).
- **Interfaces:** Prefix interfaces with `I` (e.g., `IAuthenticationFacade`).
- **Use Cases:** Suffix use case classes with `UseCase` (e.g., `CreateGeneralUserUseCase`).
- **Domain Services:** Suffix domain service classes with `DomainService` (e.g., `CreateGeneralUserDomainService`).
- **Entities:** Suffix JPA entities with `Entity` (e.g., `UserEntity`).
- **Requests and Responses:** Suffix controller request and response classes with `Request` and `Response` respectively (e.g., `CreateGeneralUserRequest`, `CreateGeneralUserResponse`).

## Code Style

- **General:**
    - Follow the official Kotlin style guide.
    - Use 4 spaces for indentation.
    - Keep line length under 100 characters.
- **Data Classes:**
    - Use data classes to represent immutable data structures.
- **Companion Objects:**
    - Use companion objects for constants and factory methods.
- **Dependency Injection:**
    - Use constructor injection to provide dependencies to classes.

## Error Handling

- **Custom Exceptions:**
    - Use custom exceptions to represent specific error conditions.
- **Global Exception Handling:**
    - Use the `@RestControllerAdvice` annotation to handle exceptions globally in the web layer.
