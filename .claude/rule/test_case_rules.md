# Test Case Generation Rules for Gemini

This document outlines the common principles and structures to be followed when generating test cases for `adapter`, `domain`, and `usecase` layers within this project. These rules aim to ensure consistency, maintainability, and comprehensive test coverage.

## General Principles

1.  **Clarity and Readability**: Test cases should be easy to understand, clearly stating what is being tested and what the expected outcome is.
2.  **Isolation**: Each test case should ideally test a single unit of functionality in isolation, minimizing dependencies on external systems or other test cases.
3.  **Determinism**: Tests must produce the same result every time they are run, regardless of the environment or execution order.
4.  **Coverage**: Aim for high test coverage, ensuring that all critical paths, edge cases, and error conditions are covered.
5.  **Naming Conventions**: Follow clear and consistent naming conventions for test classes, methods, and variables.

## Layer-Specific Considerations

### 1. Domain Layer Tests (Unit Tests)

*   **Focus**: Core business logic, entities, value objects, and aggregates.
*   **Scope**: Test individual domain objects and their methods in isolation.
*   **Dependencies**: Mock or stub any external dependencies (e.g., repositories, external services) to ensure true unit testing.
*   **Assertions**: Verify that domain rules are correctly applied, state changes occur as expected, and exceptions are thrown under invalid conditions.
*   **Example Structure**:
    ```kotlin
    class MyDomainEntityTest {
        @Test
        fun `should do something when condition is met`() {
            // Given
            val entity = MyDomainEntity(...)

            // When
            entity.doSomething()

            // Then
            assertThat(entity.property).isEqualTo(expectedValue)
        }

        @Test
        fun `should throw exception when invalid input`() {
            // Given
            val entity = MyDomainEntity(...)

            // When / Then
            assertThrows<IllegalArgumentException> {
                entity.doSomethingInvalid()
            }
        }
    }
    ```

### 2. Use Case Layer Tests (Application Service Tests)

*   **Focus**: Orchestration of domain logic, interaction with repositories, and handling of input/output DTOs.
*   **Scope**: Test the use case (application service) as a whole, ensuring it correctly coordinates domain objects and interacts with infrastructure.
*   **Dependencies**: Mock or stub repositories and other infrastructure components (e.g., external APIs, event publishers) that the use case interacts with.
*   **Assertions**: Verify that the use case calls the correct domain methods, interacts with repositories as expected, and returns the correct output DTOs.
*   **Example Structure**:
    ```kotlin
    @WebMvcTest
    class MyUseCaseTest {
        private val mockRepository: MyRepository = mockk()
        private val myUseCase = MyUseCase(mockRepository)

        @Test
        fun `should process request and save data`() {
            // Given
            val command = MyCommand(...)
            every { mockRepository.save(any()) } returns Unit

            // When
            myUseCase.execute(command)

            // Then
            verify(exactly = 1) { mockRepository.save(any()) }
            // Further assertions on the state of mocked objects or returned value
        }

        @Test
        fun `should handle invalid command`() {
            // Given
            val invalidCommand = MyCommand(...)
            // No mock setup needed if validation happens before repository interaction

            // When / Then
            assertThrows<ValidationException> {
                myUseCase.execute(invalidCommand)
            }
        }
    }
    ```

### 3. Adapter Layer Tests (Integration/Controller Tests)

*   **Focus**: Interaction with external systems (e.g., REST APIs, databases, message queues), data serialization/deserialization, and error handling at the boundary.
*   **Scope**: Test the adapter's ability to correctly translate external requests/responses to/from domain/use case objects, and its interaction with the underlying infrastructure.
*   **Dependencies**: 
    *   **Controller Tests**: Focus on verifying the correct handling of incoming requests and outgoing responses, including data mapping and error handling at the API boundary. Mock the use case layer to isolate the controller's behavior.
    *   **Repository/Persistence Adapter Tests**: Use in-memory databases or test containers to test actual database interactions.
*   **Assertions**: 
    *   **Controller Tests**: Verify HTTP status codes, response bodies, and correct mapping of request parameters.
    *   **Persistence Adapter Tests**: Verify data persistence, retrieval, and updates in the database.

## Test Case Generation Workflow

When asked to generate test cases, I will:
1.  Identify the layer (domain, use case, adapter) of the component to be tested.
2.  Refer to the specific guidelines for that layer.
3.  Analyze the component's code to identify:
    *   Inputs and outputs.
    *   Dependencies and their interactions.
    *   Business rules and validation logic.
    *   Error conditions and exception handling.
4.  Generate test cases covering:
    *   Happy paths (normal execution).
    *   Edge cases (boundary conditions, empty inputs, nulls where applicable).
    *   Error paths (invalid inputs, dependency failures).
5.  Provide the generated test code, adhering to the project's existing testing framework (e.g., Kotest, JUnit) and mocking library (e.g., MockK).