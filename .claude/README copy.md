# 🍽️ Prototype Reservation System - Project Documentation

## 📖 Table of Contents

- [🏗️ Architecture Overview](architecture/README.md)
- [🚀 Getting Started](getting-started/README.md)
- [📚 API Documentation](api/README.md)
- [🔧 Development Guide](development/README.md)
- [🧪 Testing Strategy](testing/README.md)
- [📦 Deployment Guide](deployment/README.md)
- [🔐 Security Guidelines](security/README.md)
- [📊 Quality Assurance](quality/README.md)

## 🎯 Project Overview

**Prototype Reservation System** is a comprehensive restaurant reservation platform built with **Spring Boot 3.4.5** and **Kotlin 2.0.10**, demonstrating enterprise-grade software engineering practices through **Hexagonal Architecture** and **Domain-Driven Design**.

### ✨ Key Features

- 🏛️ **Hexagonal Architecture** - Clean separation of concerns with ports and adapters
- 🎯 **Domain-Driven Design** - Rich domain models with business logic encapsulation
- 🔒 **JWT Security** - Secure authentication with role-based authorization
- 🗄️ **Multi-layered Persistence** - JPA entities with QueryDSL for type-safe queries
- 🧪 **Comprehensive Testing** - Layer-specific testing with Kotest, MockK, and Testcontainers
- 📈 **Quality Enforcement** - Zero-tolerance for code quality violations
- 🚀 **CI/CD Ready** - Docker Compose, SonarQube, and CodeCov integration

### 🏢 Business Domain

The system manages restaurant reservations with three primary user roles:
- **👤 General Users**: Browse restaurants, make reservations, manage profiles
- **🏪 Restaurant Owners**: Manage restaurant information, view reservations, handle business operations
- **👨‍💼 Administrators**: System oversight, user management, business intelligence

### 🛠️ Technology Stack

| Category | Technologies |
|----------|-------------|
| **Core Framework** | Spring Boot 3.4.5, Kotlin 2.0.10, Spring Security |
| **Database** | MySQL 8.0, Redis, Flyway Migrations |
| **Testing** | Kotest, MockK, Testcontainers, JUnit 5, AssertJ |
| **Build & Quality** | Gradle 8.x, Detekt, Spotless, Jacoco, SonarQube |
| **Infrastructure** | Docker Compose, QueryDSL, Spring REST Docs |

### 📊 Project Statistics

- **5 Modules**: Hexagonal architecture implementation
- **19 REST Controllers**: Full API coverage
- **15+ Domain Entities**: Rich business models
- **Comprehensive Test Coverage**: Layer-specific testing strategies
- **Zero Code Quality Issues**: Enforced through automated tooling

## 🏗️ Architecture Highlights

### Module Structure
```
📦 prototype-reservation-system
├── 🏛️ adapter-module/          # Infrastructure Layer (Controllers, Security, Persistence)
├── 🎯 application-module/       # Application Layer (Use Cases, Ports)
├── 💎 core-module/             # Domain Layer (Entities, Domain Services)
├── 🔧 shared-module/           # Shared Kernel (Enums, Utilities, Exceptions)
└── 🧪 test-module/             # Test Utilities and Fixtures
```

### Design Principles
- **Single Responsibility**: Each module has a clear, focused purpose
- **Dependency Inversion**: Domain layer has no external dependencies
- **Interface Segregation**: Ports and adapters for clean boundaries
- **Open/Closed Principle**: Extensible design through abstractions

## 🚀 Quick Start

### Prerequisites
- **Java 21+** - Required for Spring Boot 3.4.5
- **Docker & Docker Compose** - For MySQL and Redis services
- **Gradle 8.x** - Build automation (wrapper included)

### Development Setup
```bash
# 1. Clone and setup environment
git clone <repository-url>
cd prototype-reservation-system

# 2. Start infrastructure services
docker-compose up -d

# 3. Run database migrations
./gradlew :adapter-module:flywayMigrate

# 4. Start the application
./gradlew :adapter-module:bootRun
```

### Quality Checks
```bash
# Apply code formatting
./gradlew spotlessApply

# Run static analysis
./gradlew detekt

# Execute comprehensive test suite
./gradlew test jacocoTestReport

# Pre-commit validation (automated)
./gradlew gitPreCommitHook
```

## 📈 Quality Metrics

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=newkayak12_prototype-reservation-system&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=newkayak12_prototype-reservation-system)
[![Coverage](https://img.shields.io/codecov/c/github/newkayak12/prototype-reservation-system/master?style=flat)](https://app.codecov.io/gh/newkayak12/prototype-reservation-system)

### Code Quality Standards
- **Zero Detekt Issues**: Enforced through CI/CD pipeline
- **Comprehensive Test Coverage**: Layer-specific testing strategies
- **Automated Formatting**: Spotless with Ktlint integration
- **Pre-commit Hooks**: Automatic validation before commits

## 🎓 Learning Resources

- **📚 Study Notes**: [Comprehensive learning documentation](https://newkayak12.github.io/2025/05/09/rollup-2025-01.firstHalf.html)
- **📋 Project Planning**: [GitHub Project Boards](https://github.com/users/newkayak12/projects)
- **📖 Wiki**: [Detailed technical documentation](https://github.com/newkayak12/prototype-reservation-system/wiki)
- **🎨 Event Storming**: [Domain modeling visualization](./img/eventStorming.excalidraw.svg)

---

**🏆 This project demonstrates enterprise-grade software engineering practices with a focus on maintainability, scalability, and code quality.**