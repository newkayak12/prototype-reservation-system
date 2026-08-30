# 🚀 Developer Onboarding Guide

**Project**: Prototype Reservation System  
**Target Audience**: New developers joining the team  
**Estimated Onboarding Time**: 1-2 weeks  

## Table of Contents

1. [Quick Start](#quick-start)
2. [Prerequisites](#prerequisites)
3. [Project Setup](#project-setup)
4. [Development Environment](#development-environment)
5. [Understanding the Codebase](#understanding-the-codebase)
6. [Development Workflow](#development-workflow)
7. [Testing Guidelines](#testing-guidelines)
8. [Common Tasks](#common-tasks)
9. [Troubleshooting](#troubleshooting)
10. [Resources](#resources)

---

## Quick Start

### 30-Second Overview
This is a **Spring Boot 3.4.5 + Kotlin** reservation system using **Hexagonal Architecture** and **Domain-Driven Design**. We follow strict quality standards with **zero tolerance** for code quality violations.

### 5-Minute Setup
```bash
# 1. Clone the repository
git clone <repository-url>
cd prototype-reservation-system

# 2. Start services
docker-compose up -d

# 3. Build and run
./gradlew build
./gradlew :adapter-module:bootRun
```

### First API Call
```bash
# Check if the application is running
curl http://localhost:8080/actuator/health
```

---

## Prerequisites

### Required Knowledge
- **Kotlin**: Intermediate level (understand data classes, null safety, extensions)
- **Spring Boot**: Basic understanding of dependency injection, REST APIs
- **Hexagonal Architecture**: Basic understanding of ports and adapters
- **Domain-Driven Design**: Familiarity with aggregates, entities, value objects
- **Git**: Standard workflow (clone, branch, commit, push, PR)

### Required Tools
- **JDK 21**: Install via [SDKMAN](https://sdkman.io/) or package manager
- **Docker**: For MySQL and Redis
- **Git**: Version control
- **IDE**: IntelliJ IDEA (recommended) or VS Code with Kotlin plugin

### Optional Tools
- **Postman/Insomnia**: API testing
- **TablePlus/DBeaver**: Database GUI
- **Redis CLI**: Redis inspection

---

## Project Setup

### 1. Environment Setup
```bash
# Install SDKMAN (if not already installed)
curl -s "https://get.sdkman.io" | bash

# Install JDK 21
sdk install java 21.0.1-tem
sdk use java 21.0.1-tem

# Verify installation
java -version
# Should show: openjdk version "21.0.1"
```

### 2. Clone and Initial Setup
```bash
# Clone the repository
git clone <repository-url>
cd prototype-reservation-system

# Make gradlew executable
chmod +x gradlew

# Install pre-commit hooks
./gradlew gitPreCommitHook
```

### 3. Start Infrastructure Services
```bash
# Start MySQL and Redis
docker-compose up -d

# Verify services are running
docker-compose ps
# Should show mysql and redis containers as "Up"

# Check logs if needed
docker-compose logs -f mysql
```

### 4. Initial Build and Test
```bash
# Clean build (first time)
./gradlew clean build

# Run tests
./gradlew test

# Start the application
./gradlew :adapter-module:bootRun
```

### 5. Verify Setup
```bash
# Health check
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}

# Database connection test
curl http://localhost:8080/api/v1/category/cuisines
# Should return list of cuisine categories
```

---

## Development Environment

### IDE Configuration (IntelliJ IDEA)

#### 1. Import Project
1. **File → Open** → Select project root directory
2. **Import as Gradle project**
3. **Use Gradle wrapper** (recommended)
4. **JDK 21** as project SDK

#### 2. Code Style Setup
1. **Settings → Editor → Code Style → Kotlin**
2. **Import from** → `detekt.yaml` (automatic with detekt plugin)
3. **Enable Ktlint** formatting rules

#### 3. Recommended Plugins
- **Kotlin** (bundled)
- **Detekt** (code quality)
- **Spring Boot** (bundled)
- **Database Tools** (bundled)
- **Docker** (container management)

#### 4. Run Configurations
Create these run configurations for efficient development:

**Application Run**:
- **Main class**: `com.reservation.ReservationApplicationKt`
- **Module**: `adapter-module.main`
- **Environment variables**: `SPRING_PROFILES_ACTIVE=local`

**Test Run (All)**:
- **Type**: Gradle
- **Tasks**: `test`
- **Arguments**: `--parallel`

**Quality Check**:
- **Type**: Gradle
- **Tasks**: `spotlessApply detekt test`

### VS Code Configuration (Alternative)

#### Extensions
```json
{
  "recommendations": [
    "fwcd.kotlin",
    "vscjava.vscode-gradle",
    "redhat.vscode-yaml",
    "ms-vscode.vscode-json",
    "formulahendry.auto-rename-tag"
  ]
}
```

#### Settings
```json
{
  "kotlin.languageServer.enabled": true,
  "java.home": "/path/to/jdk-21",
  "gradle.nestedProjects": true
}
```

---

## Understanding the Codebase

### Module Overview
```
prototype-reservation-system/
├── adapter-module/        # 🌐 REST APIs, Database, Security
├── application-module/    # 🔄 Use Cases, Ports
├── core-module/          # 🏛️ Domain Logic, Entities
├── shared-module/        # 🛠️ Utilities, Enums
├── test-module/          # 🧪 Test Utilities
└── .claude/              # 📚 Documentation
```

### Key Concepts to Understand

#### 1. Hexagonal Architecture Flow
```
HTTP Request → Controller → Use Case → Domain Service → Entity
              ↓
HTTP Response ← Response DTO ← Domain Object ← Repository ← Database
```

#### 2. Package Structure Convention
```kotlin
// Domain structure example
com.reservation.user/
├── self/User.kt                    // Aggregate root
├── policy/UserValidationPolicy.kt  // Business rules
├── service/UserDomainService.kt    // Domain service
└── vo/PersonalAttributes.kt        // Value objects
```

#### 3. Naming Conventions
- **Entities**: Noun (e.g., `User`, `Restaurant`)
- **Services**: Verb + Entity + Service (e.g., `CreateUserService`)
- **Controllers**: Entity + Action + Controller (e.g., `UserSignUpController`)
- **DTOs**: Purpose + Entity + Request/Response (e.g., `CreateUserRequest`)

### Reading the Code

#### Start Here (Recommended Reading Order):
1. **Domain Models** (`core-module`):
   - `User.kt` - Understanding rich domain entities
   - `Restaurant.kt` - Complex aggregates with collections
   - Value objects in `vo/` packages

2. **Use Cases** (`application-module`):
   - `CreateGeneralUserService.kt` - Application service pattern
   - Port interfaces (`port/input` and `port/output`)

3. **Controllers** (`adapter-module`):
   - `GeneralUserSignUpController.kt` - REST API pattern
   - Request/Response DTOs

4. **Configuration** (`adapter-module/config`):
   - `SecurityConfig.kt` - Security setup
   - `PersistenceConfig.kt` - Database configuration

---

## Development Workflow

### Git Workflow

#### 1. Branch Strategy
```bash
# Create feature branch from main
git checkout main
git pull origin main
git checkout -b feature/add-reservation-system

# Work on your changes...

# Before committing, ensure code quality
./gradlew gitPreCommitHook
```

#### 2. Commit Guidelines
```bash
# Good commit messages
git commit -m "feat: add restaurant creation API endpoint"
git commit -m "fix: resolve password validation issue"
git commit -m "refactor: extract user validation policies"
git commit -m "docs: update API documentation for auth endpoints"

# Follow conventional commits pattern:
# feat: A new feature
# fix: A bug fix
# docs: Documentation only changes
# style: Changes that do not affect the meaning of the code
# refactor: A code change that neither fixes a bug nor adds a feature
# test: Adding missing tests or correcting existing tests
```

#### 3. Quality Gates (Automatic)
The pre-commit hook automatically runs:
1. **Spotless** - Code formatting
2. **Detekt** - Static analysis
3. **Git add** - Stage formatted files

### Code Review Process

#### Before Creating PR
```bash
# Run full quality check
./gradlew clean build detekt spotlessCheck

# Run all tests
./gradlew test

# Check for security issues (manual review)
# Check for performance implications
# Verify API documentation is updated
```

#### PR Template (Use this structure)
```markdown
## Description
Brief description of changes and why they were needed.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing completed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] No hardcoded secrets
```

---

## Testing Guidelines

### Testing Strategy Overview
We use **slice testing** approach with different frameworks per layer:

#### Core Module (Domain Logic)
- **Framework**: Kotest
- **Focus**: Business logic, domain rules
- **Example**:
```kotlin
class UserTest : StringSpec({
    "should throw exception when changing to same password" {
        val user = User(...)
        val currentPassword = Password("encoded123")
        
        shouldThrow<UseSamePasswordAsBeforeException> {
            user.changePassword(currentPassword)
        }
    }
})
```

#### Application Module (Use Cases)
- **Framework**: JUnit + AssertJ
- **Focus**: Workflow orchestration, integration
- **Example**:
```kotlin
@Test
fun `should create user successfully when valid data provided`() {
    // Given
    val command = CreateGeneralUserCommand(...)
    given(mockRepository.exists(any())).willReturn(false)
    
    // When
    val result = useCase.execute(command)
    
    // Then
    assertThat(result).isTrue()
    verify(mockRepository).save(any())
}
```

#### Adapter Module (Controllers & Infrastructure)
- **Framework**: Kotest + Spring Test + Testcontainers
- **Focus**: API contracts, database integration
- **Example**:
```kotlin
@SpringBootTest
@Testcontainers
class GeneralUserSignUpControllerTest : StringSpec({
    "should return 201 when user registration is successful" {
        val request = GeneralUserSignUpRequest(...)
        
        val response = mockMvc.post("/api/v1/user/general/sign-up") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
        
        response.andExpect {
            status { isCreated() }
            jsonPath("$.result") { value(true) }
        }
    }
})
```

### Running Tests

#### All Tests
```bash
./gradlew test
```

#### Module-Specific Tests
```bash
./gradlew :core-module:test
./gradlew :application-module:test
./gradlew :adapter-module:test
```

#### Test Categories
```bash
# Unit tests only
./gradlew test --tests "*Test"

# Integration tests only
./gradlew test --tests "*IntegrationTest"

# Specific test class
./gradlew test --tests "GeneralUserSignUpControllerTest"
```

#### Test Coverage
```bash
# Generate coverage report
./gradlew test jacocoTestReport

# View report
open build/reports/jacoco/test/html/index.html
```

### Writing Tests

#### Test Naming Convention
```kotlin
// Core module (Kotest)
"should throw exception when password is same as current"

// Application module (JUnit)
fun `should return success when valid command executed`()

// Adapter module (Spring Test)
"POST /api/v1/users should return 201 when valid request"
```

#### Test Data with Fixture Monkey
```kotlin
// Use Fixture Monkey for test data
val user = FixtureMonkey.create()
    .giveMeBuilder<User>()
    .set("loginId", "testuser")
    .set("email", "test@example.com")
    .sample()
```

---

## Common Tasks

### 1. Adding a New API Endpoint

#### Step 1: Define the Domain Logic (Core Module)
```kotlin
// 1. Add method to domain entity if needed
class Restaurant {
    fun addReview(review: Review) {
        reviews.add(review)
    }
}

// 2. Create domain service if complex logic
class AddRestaurantReviewDomainService {
    fun addReview(restaurant: Restaurant, reviewData: ReviewData): Review {
        // Business logic here
    }
}
```

#### Step 2: Create Use Case (Application Module)
```kotlin
// 1. Define input port interface
interface AddRestaurantReviewUseCase {
    fun execute(command: AddRestaurantReviewCommand): Boolean
}

// 2. Create command DTO
data class AddRestaurantReviewCommand(
    val restaurantId: String,
    val userId: String,
    val rating: Int,
    val comment: String
)

// 3. Implement use case
@UseCase
class AddRestaurantReviewService(
    private val loadRestaurant: LoadRestaurant,
    private val saveRestaurant: SaveRestaurant,
    private val addReviewDomainService: AddRestaurantReviewDomainService
) : AddRestaurantReviewUseCase {
    
    @Transactional
    override fun execute(command: AddRestaurantReviewCommand): Boolean {
        val restaurant = loadRestaurant.query(command.restaurantId)
        val review = addReviewDomainService.addReview(restaurant, command.toReviewData())
        restaurant.addReview(review)
        return saveRestaurant.command(restaurant)
    }
}
```

#### Step 3: Create Controller (Adapter Module)
```kotlin
// 1. Create request DTO
data class AddRestaurantReviewRequest(
    @field:NotBlank val restaurantId: String,
    @field:Min(1) @field:Max(5) val rating: Int,
    @field:Size(max = 500) val comment: String
) {
    fun toCommand(userId: String) = AddRestaurantReviewCommand(
        restaurantId = restaurantId,
        userId = userId,
        rating = rating,
        comment = comment
    )
}

// 2. Create controller
@RestController
class AddRestaurantReviewController(
    private val addRestaurantReviewUseCase: AddRestaurantReviewUseCase,
    private val extractUserFromToken: ExtractUserFromTokenUseCase
) {
    @PostMapping("/api/v1/restaurant/{restaurantId}/reviews")
    fun addReview(
        @PathVariable restaurantId: String,
        @Valid @RequestBody request: AddRestaurantReviewRequest,
        @RequestHeader("Authorization") authorization: String
    ): BooleanResponse {
        val userId = extractUserFromToken.execute(authorization)
        val command = request.toCommand(userId)
        return BooleanResponse.created(addRestaurantReviewUseCase.execute(command))
    }
}
```

#### Step 4: Write Tests
```kotlin
// Core module test
class RestaurantTest : StringSpec({
    "should add review to restaurant" {
        val restaurant = Restaurant(...)
        val review = Review(...)
        
        restaurant.addReview(review)
        
        restaurant.reviews should contain(review)
    }
})

// Application module test
class AddRestaurantReviewServiceTest {
    @Test
    fun `should add review successfully when valid command provided`() {
        // Given, When, Then
    }
}

// Adapter module test
@SpringBootTest
class AddRestaurantReviewControllerTest : StringSpec({
    "POST /api/v1/restaurant/{id}/reviews should return 201 when valid request" {
        // Test implementation
    }
})
```

### 2. Adding Database Migration

#### Create Migration File
```sql
-- V1_12__add_restaurant_reviews.sql
CREATE TABLE restaurant_reviews (
    id VARCHAR(36) PRIMARY KEY,
    restaurant_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_datetime DATETIME NOT NULL,
    updated_datetime DATETIME NOT NULL,
    
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    
    INDEX idx_restaurant_reviews_restaurant_id (restaurant_id),
    INDEX idx_restaurant_reviews_user_id (user_id)
);
```

#### Run Migration
```bash
./gradlew :adapter-module:flywayMigrate
```

### 3. Adding Configuration Property

#### Add to application.yaml
```yaml
# adapter-module/src/main/resources/application.yaml
app:
  review:
    max-length: 500
    moderation-enabled: true
```

#### Create Configuration Class
```kotlin
@ConfigurationProperties(prefix = "app.review")
data class ReviewProperties(
    val maxLength: Int = 500,
    val moderationEnabled: Boolean = false
)
```

#### Use in Service
```kotlin
@UseCase
class AddRestaurantReviewService(
    private val reviewProperties: ReviewProperties
) {
    fun execute(command: AddRestaurantReviewCommand): Boolean {
        if (command.comment.length > reviewProperties.maxLength) {
            throw CommentTooLongException()
        }
        // ...
    }
}
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Application Won't Start

**Error**: `Connection refused to MySQL`
```bash
# Check if MySQL is running
docker-compose ps

# Restart services
docker-compose down
docker-compose up -d

# Check logs
docker-compose logs mysql
```

**Error**: `Port 8080 already in use`
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or use different port
./gradlew :adapter-module:bootRun --args='--server.port=8081'
```

#### 2. Tests Failing

**Error**: Test containers not starting
```bash
# Ensure Docker is running
docker ps

# Clean test containers
docker container prune -f
docker volume prune -f
```

**Error**: Database schema issues
```bash
# Reset test database
./gradlew :adapter-module:flywayClean :adapter-module:flywayMigrate
```

#### 3. Code Quality Issues

**Error**: Detekt violations
```bash
# Run detekt to see violations
./gradlew detekt

# View detailed report
open build/reports/detekt/detekt.html

# Fix formatting issues automatically
./gradlew spotlessApply
```

**Error**: Import issues
```bash
# Refresh Gradle in IDE
./gradlew clean build --refresh-dependencies

# In IntelliJ: File → Invalidate Caches and Restart
```

#### 4. Git Hooks Issues

**Error**: Pre-commit hook failing
```bash
# Fix code formatting
./gradlew spotlessApply

# Run detekt
./gradlew detekt

# Add changes and commit again
git add .
git commit -m "fix: resolve code quality issues"
```

### Getting Help

#### Internal Resources
1. **Documentation**: Check `.claude/` directory for guides
2. **Code Examples**: Look at existing similar implementations
3. **Tests**: Use tests as documentation for expected behavior

#### External Resources
1. **Spring Boot Docs**: https://docs.spring.io/spring-boot/docs/current/reference/html/
2. **Kotlin Docs**: https://kotlinlang.org/docs/
3. **Hexagonal Architecture**: https://alistair.cockburn.us/hexagonal-architecture/
4. **Domain-Driven Design**: https://martinfowler.com/tags/domain%20driven%20design.html

#### Team Communication
1. **Ask Questions Early**: Don't spend more than 30 minutes stuck
2. **Share Screen**: Use screen sharing for complex issues
3. **Code Reviews**: Learn from PR feedback
4. **Pair Programming**: Work together on complex features

---

## Resources

### Essential Reading
1. **[CLAUDE.md](../CLAUDE.md)** - Project development guide
2. **[Architecture Guide](.claude/architecture-guide.md)** - Detailed architecture documentation
3. **[API Documentation](.claude/api-documentation.md)** - Complete API reference
4. **[Domain Model Guide](.claude/domain-model-guide.md)** - Business domain documentation

### Development Tools
- **Gradle**: Build automation and dependency management
- **Docker Compose**: Local development environment
- **Spotless**: Code formatting
- **Detekt**: Static code analysis
- **Jacoco**: Test coverage reporting

### Testing Tools
- **Kotest**: Kotlin-first testing framework
- **JUnit 5**: Java testing framework
- **Testcontainers**: Integration testing with real databases
- **AssertJ**: Fluent assertions library
- **Fixture Monkey**: Test data generation

### Code Quality Tools
- **Ktlint**: Kotlin linting
- **SonarQube**: Code quality analysis (CI/CD)
- **CodeCov**: Test coverage reporting (CI/CD)

### Useful Commands Reference
```bash
# Development
./gradlew bootRun                    # Start application
./gradlew build                      # Full build
./gradlew test                       # Run all tests
./gradlew clean                      # Clean build artifacts

# Code Quality
./gradlew spotlessApply              # Fix formatting
./gradlew detekt                     # Static analysis
./gradlew jacocoTestReport          # Coverage report

# Database
./gradlew flywayMigrate             # Run migrations
./gradlew flywayClean               # Clean database
./gradlew flywayInfo                # Migration status

# Docker
docker-compose up -d                 # Start services
docker-compose down                  # Stop services
docker-compose logs -f <service>     # View logs

# Git
git checkout -b feature/branch-name  # Create feature branch
./gradlew gitPreCommitHook          # Run quality checks
git commit -m "feat: description"   # Commit changes
```

### Next Steps After Onboarding

1. **Week 1**: Complete setup, understand architecture, implement simple feature
2. **Week 2**: Write comprehensive tests, review existing code, participate in code reviews
3. **Week 3+**: Take on larger features, contribute to architecture decisions, mentor new developers

Welcome to the team! 🎉