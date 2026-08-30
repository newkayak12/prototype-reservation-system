# 🔧 Development Guide

## 📋 Table of Contents

- [Development Setup](#-development-setup)
- [Coding Standards](#-coding-standards)
- [Module Development](#-module-development)
- [Database Development](#-database-development)
- [Testing Guidelines](#-testing-guidelines)
- [Security Implementation](#-security-implementation)
- [Performance Optimization](#-performance-optimization)
- [Best Practices](#-best-practices)

## 🛠️ Development Setup

### IDE Configuration

#### IntelliJ IDEA Setup

```kotlin
// Recommended plugins
Kotlin
Spring Boot
JPA Buddy
Database Navigator
SonarLint
Detekt

// Code style settings
File -> Settings -> Editor -> Code Style -> Kotlin
- Import scheme from: .editorconfig
- Continuation indent: 4
- Keep when reformatting: Line breaks, Blank lines in declarations

// Annotation processing
File -> Settings -> Build -> Annotation Processors
- Enable annotation processing: ✓
- Processor path: Use classpath of module
```

#### Gradle Configuration

```kotlin
// build.gradle.kts key configurations
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// Quality tools
spotless {
    kotlin {
        ktlint("1.2.1")
        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }
}

detekt {
    config.setFrom(files("$rootDir/detekt.yaml"))
    buildUponDefaultConfig = true
}
```

### Development Environment Variables

```yaml
# Local development (.env or application-local.yaml)
spring:
  profiles:
    active: local
  datasource:
    url: jdbc:mysql://localhost:3306/prototype_reservation
    username: root
    password: reservation
  redis:
    host: localhost
    port: 6379
    
# Security settings
jwt:
  secret: your-super-secret-key-for-development-only
  access-token-expiry: 3600000  # 1 hour
  refresh-token-expiry: 604800000  # 7 days

# Logging levels
logging:
  level:
    com.reservation: DEBUG
    org.springframework.security: DEBUG
    org.hibernate.SQL: DEBUG
```

## 📏 Coding Standards

### Kotlin Code Style

#### Naming Conventions

```kotlin
// Classes: PascalCase
class UserService
class RestaurantRepository
data class CreateUserCommand

// Functions: camelCase
fun findUserById(id: UUID): User?
fun validateEmail(email: String): Boolean

// Properties: camelCase
val userName: String
var isActive: Boolean
private val maxRetryCount: Int = 3

// Constants: SCREAMING_SNAKE_CASE
const val MAX_PASSWORD_LENGTH = 128
const val DEFAULT_PAGE_SIZE = 20

// Packages: lowercase with dots
package com.reservation.user.service
package com.reservation.restaurant.policy
```

#### Code Structure

```kotlin
// File structure order
1. Package declaration
2. Imports (grouped: standard library, third-party, project)
3. File-level constants
4. Class declaration
5. Properties (companion object first, then instance)
6. Constructors
7. Functions (public first, then private)

// Example
package com.reservation.user.service

import java.util.UUID
import org.springframework.stereotype.Service
import com.reservation.user.User
import com.reservation.user.port.FindUser

@Service
class UserService(
    private val findUser: FindUser
) {
    companion object {
        private const val MAX_LOGIN_ATTEMPTS = 5
    }
    
    fun findById(id: UUID): User? {
        return findUser.findById(id)
    }
    
    private fun validateUser(user: User): Boolean {
        // validation logic
    }
}
```

#### Documentation Standards

```kotlin
/**
 * Service for managing user authentication and profile operations.
 * 
 * This service handles user sign-in, profile updates, and access control
 * following domain-driven design principles.
 * 
 * @property findUser Repository for user data access
 * @property passwordEncoder Service for password hashing
 * @author Development Team
 * @since 1.0.0
 */
@Service
class UserService(
    private val findUser: FindUser,
    private val passwordEncoder: PasswordEncoder
) {
    /**
     * Authenticates user with provided credentials.
     * 
     * @param loginId User's login identifier
     * @param password Raw password for authentication
     * @return Authentication result with user details or null if failed
     * @throws AuthenticationException when user is blocked or credentials invalid
     */
    fun authenticate(loginId: String, password: String): AuthenticationResult? {
        // implementation
    }
}
```

### Domain-Driven Design Patterns

#### Aggregate Structure

```kotlin
// Aggregate Root
class User private constructor(
    val id: UUID,
    val loginId: String,
    private var nickname: String,
    private var email: String,
    private var status: UserStatus
) {
    companion object {
        fun create(form: CreateUserForm): User {
            validateForm(form)
            return User(
                id = UUID.randomUUID(),
                loginId = form.loginId,
                nickname = form.nickname,
                email = form.email,
                status = UserStatus.ACTIVE
            )
        }
    }
    
    // Business methods
    fun changeNickname(newNickname: String) {
        require(newNickname.isNotBlank()) { "Nickname cannot be blank" }
        this.nickname = newNickname
    }
    
    fun deactivate() {
        require(status == UserStatus.ACTIVE) { "User is not active" }
        this.status = UserStatus.INACTIVE
    }
}

// Value Object
data class UserAddress(
    val street: String,
    val city: String,
    val postalCode: String,
    val country: String
) {
    init {
        require(street.isNotBlank()) { "Street cannot be blank" }
        require(city.isNotBlank()) { "City cannot be blank" }
        require(postalCode.matches(Regex("\\d{5}(-\\d{4})?"))) { "Invalid postal code" }
    }
}

// Domain Service
@Component
class UserDomainService {
    fun canChangePassword(user: User, newPassword: String): Boolean {
        return when {
            user.status != UserStatus.ACTIVE -> false
            newPassword == user.currentPassword -> false
            newPassword.length < 8 -> false
            else -> true
        }
    }
}
```

#### Port Definitions

```kotlin
// Input Port (Use Case Interface)
interface CreateUserUseCase {
    fun create(command: CreateUserCommand): UserCreatedEvent
}

// Output Port (Repository Interface)
interface FindUser {
    fun findById(id: UUID): User?
    fun findByLoginId(loginId: String): User?
    fun existsByLoginId(loginId: String): Boolean
}

interface SaveUser {
    fun save(user: User): User
}

// Command/Query Objects
data class CreateUserCommand(
    val loginId: String,
    val password: String,
    val nickname: String,
    val email: String
)

data class UserCreatedEvent(
    val userId: UUID,
    val loginId: String,
    val createdAt: Instant
)
```

## 🏗️ Module Development

### Adding New Features

#### 1. Domain Layer Development

```kotlin
// 1. Create domain entity
// core-module/src/main/kotlin/com/reservation/booking/Booking.kt
class Booking private constructor(
    val id: UUID,
    val userId: UUID,
    val restaurantId: UUID,
    val bookingDate: LocalDate,
    val bookingTime: LocalTime,
    private var status: BookingStatus
) {
    companion object {
        fun create(form: CreateBookingForm): Booking {
            // validation and creation logic
        }
    }
    
    fun confirm() {
        require(status == BookingStatus.PENDING) { "Booking is not pending" }
        this.status = BookingStatus.CONFIRMED
    }
}

// 2. Create domain service if needed
// core-module/src/main/kotlin/com/reservation/booking/service/BookingDomainService.kt
@Component
class BookingDomainService {
    fun canCreateBooking(userId: UUID, restaurantId: UUID, dateTime: LocalDateTime): Boolean {
        // Complex business logic
    }
}
```

#### 2. Application Layer Development

```kotlin
// 3. Define ports
// application-module/src/main/kotlin/com/reservation/booking/port/input/CreateBookingUseCase.kt
interface CreateBookingUseCase {
    fun create(command: CreateBookingCommand): BookingCreatedEvent
}

// application-module/src/main/kotlin/com/reservation/booking/port/output/FindBooking.kt
interface FindBooking {
    fun findById(id: UUID): Booking?
    fun findByUserId(userId: UUID, pageable: Pageable): Page<Booking>
}

// 4. Implement use case
// application-module/src/main/kotlin/com/reservation/booking/usecase/CreateBookingService.kt
@UseCase
class CreateBookingService(
    private val findBooking: FindBooking,
    private val saveBooking: SaveBooking,
    private val bookingDomainService: BookingDomainService
) : CreateBookingUseCase {
    
    override fun create(command: CreateBookingCommand): BookingCreatedEvent {
        // Use case implementation
        require(bookingDomainService.canCreateBooking(
            command.userId, 
            command.restaurantId, 
            command.dateTime
        )) { "Cannot create booking" }
        
        val booking = Booking.create(command.toCreateForm())
        val savedBooking = saveBooking.save(booking)
        
        return BookingCreatedEvent(
            bookingId = savedBooking.id,
            userId = savedBooking.userId,
            createdAt = Instant.now()
        )
    }
}
```

#### 3. Infrastructure Layer Development

```kotlin
// 5. Create JPA entity
// adapter-module/src/main/kotlin/com/reservation/persistence/booking/entity/BookingEntity.kt
@Entity
@Table(name = "bookings")
class BookingEntity : TimeBasedPrimaryKey(), AuditDateTime {
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID()
    
    @Column(name = "restaurant_id", nullable = false) 
    var restaurantId: UUID = UUID.randomUUID()
    
    @Column(name = "booking_date", nullable = false)
    var bookingDate: LocalDate = LocalDate.now()
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: BookingStatus = BookingStatus.PENDING
    
    fun toDomain(): Booking {
        // Mapping logic
    }
}

// 6. Implement repository adapter
// adapter-module/src/main/kotlin/com/reservation/persistence/booking/adapter/BookingRepositoryAdapter.kt
@Repository
class BookingRepositoryAdapter(
    private val bookingJpaRepository: BookingJpaRepository
) : FindBooking, SaveBooking {
    
    override fun findById(id: UUID): Booking? {
        return bookingJpaRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)
    }
    
    override fun save(booking: Booking): Booking {
        val entity = BookingEntity.fromDomain(booking)
        return bookingJpaRepository.save(entity).toDomain()
    }
}

// 7. Create REST controller
// adapter-module/src/main/kotlin/com/reservation/rest/booking/CreateBookingController.kt
@RestController
@RequestMapping("/api/bookings")
@Validated
class CreateBookingController(
    private val createBookingUseCase: CreateBookingUseCase
) {
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    fun createBooking(
        @Valid @RequestBody request: CreateBookingRequest,
        authentication: Authentication
    ): ResponseEntity<BookingResponse> {
        val command = CreateBookingCommand(
            userId = UUID.fromString(authentication.name),
            restaurantId = request.restaurantId,
            bookingDate = request.bookingDate,
            bookingTime = request.bookingTime
        )
        
        val event = createBookingUseCase.create(command)
        return ResponseEntity.ok(BookingResponse.from(event))
    }
}
```

## 🗄️ Database Development

### Migration Best Practices

#### Flyway Migration Structure

```sql
-- V1_12__create_booking_context.sql
-- Create booking-related tables with proper constraints

-- Create bookings table
CREATE TABLE bookings (
    id CHAR(36) NOT NULL PRIMARY KEY COMMENT 'Time-based UUID primary key',
    user_id CHAR(36) NOT NULL COMMENT 'Reference to users.id',
    restaurant_id CHAR(36) NOT NULL COMMENT 'Reference to restaurants.id',
    booking_date DATE NOT NULL COMMENT 'Date of booking',
    booking_time TIME NOT NULL COMMENT 'Time of booking',
    party_size INT NOT NULL DEFAULT 1 COMMENT 'Number of people',
    status ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED') NOT NULL DEFAULT 'PENDING',
    special_requests TEXT COMMENT 'Customer special requests',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE NOT NULL COMMENT 'Soft delete flag',
    
    INDEX idx_bookings_user_id (user_id),
    INDEX idx_bookings_restaurant_id (restaurant_id),
    INDEX idx_bookings_booking_date (booking_date),
    INDEX idx_bookings_status (status),
    INDEX idx_bookings_created_at (created_at),
    
    CONSTRAINT fk_bookings_user_id 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_bookings_restaurant_id 
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) 
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_bookings_party_size 
        CHECK (party_size > 0 AND party_size <= 20),
    CONSTRAINT chk_bookings_booking_time 
        CHECK (booking_time BETWEEN '09:00:00' AND '23:59:59')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
  COMMENT='Restaurant booking records';

-- Create booking history table for audit trail
CREATE TABLE booking_histories (
    id CHAR(36) NOT NULL PRIMARY KEY,
    booking_id CHAR(36) NOT NULL,
    changed_by CHAR(36) NOT NULL COMMENT 'User who made the change',
    change_type ENUM('CREATED', 'CONFIRMED', 'MODIFIED', 'CANCELLED') NOT NULL,
    old_values JSON COMMENT 'Previous values before change',
    new_values JSON COMMENT 'New values after change',
    change_reason VARCHAR(500) COMMENT 'Reason for the change',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    INDEX idx_booking_histories_booking_id (booking_id),
    INDEX idx_booking_histories_changed_by (changed_by),
    INDEX idx_booking_histories_change_type (change_type),
    INDEX idx_booking_histories_created_at (created_at),
    
    CONSTRAINT fk_booking_histories_booking_id 
        FOREIGN KEY (booking_id) REFERENCES bookings(id) 
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
  COMMENT='Booking change history for audit purposes';
```

#### QueryDSL Integration

```kotlin
// Generated Q-classes usage
@Repository
class BookingQueryRepository(
    private val queryFactory: JPAQueryFactory
) {
    fun findBookingsByDateRange(
        restaurantId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<BookingEntity> {
        return queryFactory
            .selectFrom(bookingEntity)
            .where(
                bookingEntity.restaurantId.eq(restaurantId)
                    .and(bookingEntity.bookingDate.between(startDate, endDate))
                    .and(bookingEntity.isDeleted.eq(false))
            )
            .orderBy(bookingEntity.bookingDate.asc(), bookingEntity.bookingTime.asc())
            .fetch()
    }
    
    fun findBookingConflicts(
        restaurantId: UUID,
        bookingDate: LocalDate,
        bookingTime: LocalTime,
        partySize: Int
    ): List<BookingEntity> {
        val timeStart = bookingTime.minusHours(2)
        val timeEnd = bookingTime.plusHours(2)
        
        return queryFactory
            .selectFrom(bookingEntity)
            .where(
                bookingEntity.restaurantId.eq(restaurantId)
                    .and(bookingEntity.bookingDate.eq(bookingDate))
                    .and(bookingEntity.bookingTime.between(timeStart, timeEnd))
                    .and(bookingEntity.status.`in`(BookingStatus.PENDING, BookingStatus.CONFIRMED))
                    .and(bookingEntity.isDeleted.eq(false))
            )
            .fetch()
    }
}
```

## 🧪 Testing Guidelines

### Testing Strategy by Layer

#### 1. Domain Layer Testing (Kotest)

```kotlin
// core-module/src/test/kotlin/com/reservation/booking/BookingTest.kt
class BookingTest : BehaviorSpec({
    
    given("a valid booking creation form") {
        val form = CreateBookingForm(
            userId = UUID.randomUUID(),
            restaurantId = UUID.randomUUID(),
            bookingDate = LocalDate.now().plusDays(1),
            bookingTime = LocalTime.of(19, 0),
            partySize = 4
        )
        
        `when`("creating a new booking") {
            val booking = Booking.create(form)
            
            then("booking should be created with pending status") {
                booking.status shouldBe BookingStatus.PENDING
                booking.userId shouldBe form.userId
                booking.partySize shouldBe form.partySize
            }
        }
    }
    
    given("a pending booking") {
        val booking = FixtureMonkey.giveMeBuilder<Booking>()
            .set("status", BookingStatus.PENDING)
            .sample()
        
        `when`("confirming the booking") {
            booking.confirm()
            
            then("status should change to confirmed") {
                booking.status shouldBe BookingStatus.CONFIRMED
            }
        }
    }
    
    given("a non-pending booking") {
        val booking = FixtureMonkey.giveMeBuilder<Booking>()
            .set("status", BookingStatus.CONFIRMED)
            .sample()
        
        `when`("attempting to confirm") {
            val exception = shouldThrow<IllegalArgumentException> {
                booking.confirm()
            }
            
            then("should throw exception") {
                exception.message shouldContain "Booking is not pending"
            }
        }
    }
})
```

#### 2. Application Layer Testing (JUnit + MockK)

```kotlin
// application-module/src/test/kotlin/com/reservation/booking/usecase/CreateBookingServiceTest.kt
@ExtendWith(MockKExtension::class)
class CreateBookingServiceTest {
    
    @MockK
    private lateinit var findBooking: FindBooking
    
    @MockK
    private lateinit var saveBooking: SaveBooking
    
    @MockK
    private lateinit var bookingDomainService: BookingDomainService
    
    private lateinit var createBookingService: CreateBookingService
    
    @BeforeEach
    fun setUp() {
        createBookingService = CreateBookingService(
            findBooking, saveBooking, bookingDomainService
        )
    }
    
    @Test
    fun `should create booking when valid command provided`() {
        // Given
        val command = CreateBookingCommand(
            userId = UUID.randomUUID(),
            restaurantId = UUID.randomUUID(),
            bookingDate = LocalDate.now().plusDays(1),
            bookingTime = LocalTime.of(19, 0)
        )
        
        val booking = FixtureMonkey.giveMeOne<Booking>()
        
        every { bookingDomainService.canCreateBooking(any(), any(), any()) } returns true
        every { saveBooking.save(any()) } returns booking
        
        // When
        val result = createBookingService.create(command)
        
        // Then
        assertThat(result.bookingId).isNotNull
        assertThat(result.userId).isEqualTo(command.userId)
        
        verify { bookingDomainService.canCreateBooking(command.userId, command.restaurantId, any()) }
        verify { saveBooking.save(any()) }
    }
    
    @Test
    fun `should throw exception when booking cannot be created`() {
        // Given
        val command = CreateBookingCommand(
            userId = UUID.randomUUID(),
            restaurantId = UUID.randomUUID(),
            bookingDate = LocalDate.now().plusDays(1),
            bookingTime = LocalTime.of(19, 0)
        )
        
        every { bookingDomainService.canCreateBooking(any(), any(), any()) } returns false
        
        // When & Then
        assertThrows<IllegalArgumentException> {
            createBookingService.create(command)
        }
        
        verify { bookingDomainService.canCreateBooking(any(), any(), any()) }
        verify(exactly = 0) { saveBooking.save(any()) }
    }
}
```

#### 3. Infrastructure Layer Testing (Testcontainers)

```kotlin
// adapter-module/src/test/kotlin/com/reservation/booking/CreateBookingControllerTest.kt
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreateBookingControllerTest {
    
    @Container
    companion object {
        @JvmStatic
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("test_reservation")
            .withUsername("test")
            .withPassword("test123")
            .withInitScript("test-data.sql")
    }
    
    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    
    @MockBean
    private lateinit var createBookingUseCase: CreateBookingUseCase
    
    @Test
    @WithMockUser(roles = ["USER"])
    fun `should create booking successfully`() {
        // Given
        val request = CreateBookingRequest(
            restaurantId = UUID.randomUUID(),
            bookingDate = LocalDate.now().plusDays(1),
            bookingTime = LocalTime.of(19, 0),
            partySize = 4
        )
        
        val event = BookingCreatedEvent(
            bookingId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            createdAt = Instant.now()
        )
        
        every { createBookingUseCase.create(any()) } returns event
        
        // When & Then
        mockMvc.perform(
            post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.bookingId").value(event.bookingId.toString()))
        .andDo(document("create-booking"))
        
        verify { createBookingUseCase.create(any()) }
    }
}
```

## 🔐 Security Implementation

### JWT Authentication

```kotlin
// JWT Configuration
@Configuration
class JWTProviderConfig(
    private val jwtProperties: JWTProperties
) {
    @Bean
    fun jwtProvider(): JWTProvider {
        return JWTProvider(
            secret = jwtProperties.secret,
            accessTokenExpiry = jwtProperties.accessTokenExpiry,
            refreshTokenExpiry = jwtProperties.refreshTokenExpiry
        )
    }
}

// JWT Filter Implementation
@Component
class JwtFilter(
    private val jwtProvider: JWTProvider,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractToken(request)
        
        if (token != null && jwtProvider.validateToken(token)) {
            val authentication = getAuthentication(token)
            SecurityContextHolder.getContext().authentication = authentication
        }
        
        filterChain.doFilter(request, response)
    }
    
    private fun extractToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken?.startsWith("Bearer ") == true) {
            bearerToken.substring(7)
        } else null
    }
}
```

### Method-Level Security

```kotlin
@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('USER')")
class BookingController {
    
    @GetMapping("/{id}")
    @PreAuthorize("@bookingSecurityService.canViewBooking(authentication.name, #id)")
    fun getBooking(@PathVariable id: UUID): BookingResponse {
        // Implementation
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("@bookingSecurityService.canModifyBooking(authentication.name, #id)")
    fun updateBooking(
        @PathVariable id: UUID,
        @RequestBody request: UpdateBookingRequest
    ): BookingResponse {
        // Implementation
    }
}

@Service
class BookingSecurityService {
    fun canViewBooking(userId: String, bookingId: UUID): Boolean {
        // Check if user owns the booking or has admin role
    }
    
    fun canModifyBooking(userId: String, bookingId: UUID): Boolean {
        // Check modification permissions
    }
}
```

## ⚡ Performance Optimization

### Caching Strategy

```kotlin
// Redis Configuration
@Configuration
@EnableCaching
class CacheConfig {
    
    @Bean
    fun cacheManager(redisConnectionFactory: RedisConnectionFactory): CacheManager {
        val cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer()))
        
        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(cacheConfig)
            .transactionAware()
            .build()
    }
}

// Service with caching
@Service
class RestaurantService {
    
    @Cacheable(value = ["restaurants"], key = "#id")
    fun findById(id: UUID): Restaurant? {
        return restaurantRepository.findById(id)
    }
    
    @CacheEvict(value = ["restaurants"], key = "#restaurant.id")
    fun updateRestaurant(restaurant: Restaurant): Restaurant {
        return restaurantRepository.save(restaurant)
    }
    
    @Cacheable(value = ["restaurant-search"], key = "#criteria.hashCode()")
    fun searchRestaurants(criteria: SearchCriteria): List<Restaurant> {
        return restaurantRepository.search(criteria)
    }
}
```

### Database Optimization

```kotlin
// JPA Query Optimization
@Repository
class RestaurantRepositoryAdapter {
    
    // Use @EntityGraph to avoid N+1 queries
    @EntityGraph(attributePaths = ["photos", "workingDays", "cuisines"])
    fun findByIdWithDetails(id: UUID): RestaurantEntity?
    
    // Projection for read-only operations
    @Query("SELECT new com.reservation.restaurant.dto.RestaurantSummary(r.id, r.name, r.averageRating) FROM RestaurantEntity r WHERE r.isDeleted = false")
    fun findAllSummaries(pageable: Pageable): Page<RestaurantSummary>
    
    // Batch operations
    @Modifying
    @Query("UPDATE RestaurantEntity r SET r.averageRating = :rating WHERE r.id = :id")
    fun updateRating(@Param("id") id: UUID, @Param("rating") rating: Double)
}
```

## 🏆 Best Practices

### Error Handling

```kotlin
// Global Exception Handler
@RestControllerAdvice
class RestControllerExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.map { error ->
            ValidationError(
                field = error.field,
                rejectedValue = error.rejectedValue?.toString(),
                message = error.defaultMessage ?: "Validation failed"
            )
        }
        
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                details = errors
            )
        )
    }
    
    @ExceptionHandler(BusinessRuleViolationException::class)
    fun handleBusinessRuleViolation(ex: BusinessRuleViolationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.unprocessableEntity().body(
            ErrorResponse(
                code = ex.code,
                message = ex.message ?: "Business rule violation"
            )
        )
    }
}
```

### Logging Strategy

```kotlin
// Structured logging
@Service
class BookingService {
    companion object {
        private val logger = LoggerFactory.getLogger<BookingService>()
    }
    
    fun createBooking(command: CreateBookingCommand): BookingCreatedEvent {
        logger.info("Creating booking for user: ${command.userId}, restaurant: ${command.restaurantId}")
        
        try {
            val booking = Booking.create(command.toCreateForm())
            val savedBooking = saveBooking.save(booking)
            
            logger.info("Booking created successfully: ${savedBooking.id}")
            return BookingCreatedEvent.from(savedBooking)
            
        } catch (ex: Exception) {
            logger.error("Failed to create booking for user: ${command.userId}", ex)
            throw ex
        }
    }
}

// Request/Response logging
@Component
class RequestLoggingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            logger.info(
                "Request completed: {} {} - Status: {}, Duration: {}ms",
                request.method,
                request.requestURI,
                response.status,
                duration
            )
        }
    }
}
```

### Configuration Management

```kotlin
// Type-safe configuration
@ConfigurationProperties(prefix = "app.booking")
@ConstructorBinding
data class BookingProperties(
    val maxAdvanceBookingDays: Int = 30,
    val maxPartySize: Int = 20,
    val defaultBookingDuration: Duration = Duration.ofHours(2),
    val allowCancellationHours: Int = 24
)

// Environment-specific configs
# application-local.yaml
app:
  booking:
    max-advance-booking-days: 90
    max-party-size: 20
    
logging:
  level:
    com.reservation: DEBUG
    
# application-production.yaml  
app:
  booking:
    max-advance-booking-days: 30
    max-party-size: 15
    
logging:
  level:
    com.reservation: INFO
```

---

**🏆 Following these development guidelines ensures consistent, maintainable, and high-quality code across the entire project.**