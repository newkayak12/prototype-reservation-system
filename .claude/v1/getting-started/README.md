# 🚀 Getting Started

## 📋 Table of Contents

- [Prerequisites](#-prerequisites)
- [Environment Setup](#-environment-setup)
- [Development Environment](#-development-environment)
- [Running the Application](#-running-the-application)
- [Development Workflow](#-development-workflow)
- [Troubleshooting](#-troubleshooting)

## 🔧 Prerequisites

### Required Software

| Software | Version | Purpose | Installation |
|----------|---------|---------|-------------|
| **Java** | 21+ | Runtime environment | [OpenJDK 21](https://adoptium.net/) |
| **Docker** | 20.10+ | Container runtime | [Docker Desktop](https://www.docker.com/products/docker-desktop/) |
| **Docker Compose** | 2.0+ | Multi-container orchestration | Included with Docker Desktop |
| **Git** | 2.30+ | Version control | [Git SCM](https://git-scm.com/) |

### Optional but Recommended

| Software | Purpose | Installation |
|----------|---------|-------------|
| **IntelliJ IDEA** | IDE with Kotlin support | [JetBrains IntelliJ](https://www.jetbrains.com/idea/) |
| **Postman** | API testing | [Postman](https://www.postman.com/) |
| **MySQL Workbench** | Database management | [MySQL Workbench](https://dev.mysql.com/downloads/workbench/) |

### System Requirements

```yaml
Minimum Specifications:
  CPU: 2 cores
  RAM: 8GB
  Storage: 5GB free space
  OS: macOS 10.15+, Windows 10+, Ubuntu 18.04+

Recommended Specifications:
  CPU: 4+ cores
  RAM: 16GB+
  Storage: 10GB+ free space (SSD preferred)
  OS: Latest stable versions
```

## 🛠️ Environment Setup

### 1. Clone Repository

```bash
# Clone the repository
git clone https://github.com/newkayak12/prototype-reservation-system.git
cd prototype-reservation-system

# Verify project structure
ls -la
# Expected output:
# adapter-module/
# application-module/
# core-module/
# shared-module/
# test-module/
# docker-compose.yaml
# gradlew
# ...
```

### 2. Verify Java Installation

```bash
# Check Java version (must be 21+)
java -version
# Expected output:
# openjdk version "21.0.x" 2024-xx-xx
# OpenJDK Runtime Environment (build 21.0.x+x)
# OpenJDK 64-Bit Server VM (build 21.0.x+x, mixed mode, sharing)

# Check Gradle wrapper
./gradlew --version
# Expected output:
# Gradle 8.x
# Build time: 2024-xx-xx
# Kotlin DSL: 2.0.10
```

### 3. Docker Environment Verification

```bash
# Verify Docker installation
docker --version
# Expected: Docker version 20.10.x+

docker-compose --version  
# Expected: Docker Compose version 2.x.x+

# Test Docker functionality
docker run hello-world
# Should complete successfully
```

## 🐳 Development Environment

### Infrastructure Services

The project uses Docker Compose to manage development dependencies:

```yaml
# compose.yaml overview
services:
  mysql:
    image: mysql:8.0
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: reservation
      MYSQL_DATABASE: prototype_reservation
      
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
```

### Start Infrastructure Services

```bash
# Start MySQL and Redis in detached mode
docker-compose up -d

# Verify services are running
docker-compose ps
# Expected output:
# NAME                           COMMAND                  SERVICE   STATUS    PORTS
# prototype-reservation-mysql-1  "docker-entrypoint.s…"   mysql     Up        0.0.0.0:3306->3306/tcp
# prototype-reservation-redis-1  "docker-entrypoint.s…"   redis     Up        0.0.0.0:6379->6379/tcp

# Check service logs (if needed)
docker-compose logs mysql
docker-compose logs redis
```

### Database Setup

```bash
# Run Flyway migrations to set up database schema
./gradlew :adapter-module:flywayMigrate

# Verify migration success
./gradlew :adapter-module:flywayInfo
# Should show all migrations applied successfully

# If you need to reset database
./gradlew :adapter-module:flywayClean :adapter-module:flywayMigrate
```

### Environment Configuration

The application supports multiple profiles:

```yaml
# Application profiles
local:      # Development (default)
  database: localhost:3306
  redis: localhost:6379
  
stage:      # Staging environment  
  database: stage-db-host
  redis: stage-redis-host
  
production: # Production environment
  database: prod-db-host
  redis: prod-redis-host
```

## 🏃 Running the Application

### Development Mode

```bash
# Method 1: Using Gradle wrapper (recommended)
./gradlew :adapter-module:bootRun

# Method 2: Build and run JAR
./gradlew build
java -jar adapter-module/build/libs/adapter-module-0.0.1-SNAPSHOT.jar

# Method 3: Using IDE
# Open project in IntelliJ IDEA
# Navigate to: adapter-module/src/main/kotlin/com/reservation/ReservationApplication.kt
# Right-click and select "Run ReservationApplication"
```

### Application Startup Verification

```bash
# Check application health
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Verify API endpoints
curl http://localhost:8080/api/categories/cuisines
# Should return JSON response with cuisine categories

# Check application logs
tail -f logs/application.log  # if logging to file
# Or check console output for startup messages
```

### Port Configuration

| Service | Port | Purpose | Access URL |
|---------|------|---------|------------|
| **Spring Boot App** | 8080 | Main application | http://localhost:8080 |
| **MySQL** | 3306 | Database | localhost:3306 |
| **Redis** | 6379 | Cache/Sessions | localhost:6379 |
| **Actuator** | 8080 | Health checks | http://localhost:8080/actuator |

## 🔄 Development Workflow

### Code Quality Workflow

```bash
# 1. Format code (automatically fixes style issues)
./gradlew spotlessApply

# 2. Run static analysis
./gradlew detekt

# 3. Run tests with coverage
./gradlew test jacocoTestReport

# 4. View coverage report
open build/reports/jacoco/test/html/index.html  # macOS
# or navigate to: build/reports/jacoco/test/html/index.html

# 5. Pre-commit validation (runs all quality checks)
./gradlew gitPreCommitHook
```

### Testing Workflow

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :core-module:test
./gradlew :application-module:test  
./gradlew :adapter-module:test

# Run tests with specific tags
./gradlew test --tests "*ControllerTest"
./gradlew test --tests "*ServiceTest"

# Run integration tests (requires Docker services)
docker-compose up -d
./gradlew :adapter-module:test
```

### Database Development

```bash
# Create new migration
# 1. Create file: adapter-module/src/main/resources/db/migration/V1_XX__description.sql
# 2. Write SQL migration script
# 3. Run migration
./gradlew :adapter-module:flywayMigrate

# Check migration status
./gradlew :adapter-module:flywayInfo

# Rollback to specific version (if needed)
./gradlew :adapter-module:flywayUndo -Pflyway.target=1.10
```

### Git Workflow

```bash
# 1. Create feature branch
git checkout -b feature/your-feature-name

# 2. Make changes and commit
git add .
git commit -m "feat: implement feature description"
# Note: Pre-commit hooks run automatically

# 3. Push and create pull request
git push origin feature/your-feature-name
# Create pull request via GitHub/GitLab interface

# 4. Merge after review
git checkout main
git pull origin main
git branch -d feature/your-feature-name
```

## 🐛 Troubleshooting

### Common Issues

#### Application Won't Start

```bash
# Issue: Port 8080 already in use
# Solution: Find and kill process using port
lsof -ti:8080 | xargs kill -9

# Issue: Database connection failed  
# Solution: Ensure MySQL is running
docker-compose ps mysql
docker-compose up -d mysql

# Issue: Flyway migration failed
# Solution: Check migration files and database state
./gradlew :adapter-module:flywayInfo
./gradlew :adapter-module:flywayRepair  # if needed
```

#### Build Issues

```bash
# Issue: Gradle build fails
# Solution: Clean and rebuild
./gradlew clean build

# Issue: Tests fail
# Solution: Ensure infrastructure is running
docker-compose up -d
./gradlew test

# Issue: Detekt violations
# Solution: Fix code quality issues
./gradlew detekt  # to see issues
./gradlew spotlessApply  # auto-fix formatting
```

#### Docker Issues

```bash
# Issue: Docker services won't start
# Solution: Check Docker daemon and ports
docker ps -a
docker-compose down
docker-compose up -d

# Issue: MySQL connection issues
# Solution: Reset MySQL container
docker-compose down
docker volume rm prototype-reservation-system_mysql_data
docker-compose up -d mysql
./gradlew :adapter-module:flywayMigrate
```

#### IDE Configuration

```kotlin
// IntelliJ IDEA Settings
// File -> Settings -> Build -> Build Tools -> Gradle
// - Build and run using: IntelliJ IDEA
// - Run tests using: IntelliJ IDEA

// Kotlin Code Style
// File -> Settings -> Editor -> Code Style -> Kotlin
// Import settings from: .editorconfig (automatically detected)

// Annotation Processing
// File -> Settings -> Build -> Annotation Processors
// Enable annotation processing: ✓
```

### Performance Issues

```bash
# Issue: Slow startup time
# Solutions:
# 1. Increase JVM heap size
export JAVA_OPTS="-Xmx2G -Xms1G"
./gradlew :adapter-module:bootRun

# 2. Use Gradle daemon
echo "org.gradle.daemon=true" >> ~/.gradle/gradle.properties

# 3. Parallel builds
echo "org.gradle.parallel=true" >> ~/.gradle/gradle.properties
```

### Getting Help

- **📚 Documentation**: Check `docs/` directory for detailed guides
- **🐛 Issues**: Report bugs via GitHub issues
- **💬 Discussions**: Use GitHub discussions for questions
- **📖 Wiki**: Comprehensive technical documentation
- **📝 Code Comments**: Inline documentation in source code

---

**🎉 You're now ready to start developing with the Prototype Reservation System!**