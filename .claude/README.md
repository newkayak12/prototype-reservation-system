# 🍽️ Prototype Reservation System - Documentation Index

Welcome to the **Prototype Reservation System** documentation suite. This directory contains comprehensive guides for developers, architects, and stakeholders.

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

- **6 Modules**: Hexagonal architecture implementation with batch processing
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
├── 🔄 batch-module/            # Batch Processing (Monthly TimeTable Generation)
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

## 📋 Documentation Overview

### 🚀 Quick Start
- **[../CLAUDE.md](../CLAUDE.md)** - Claude Code integration guide and development commands
- **[Developer Onboarding](developer-onboarding.md)** - Complete setup guide for new team members

### 🏗️ Architecture & Design
- **[Architecture Guide](architecture-guide.md)** - Hexagonal Architecture + DDD implementation
- **[Domain Model Guide](domain-model-guide.md)** - Business domain documentation and patterns
- **[Code Analysis Report](code-analysis-report.md)** - Comprehensive codebase quality assessment

### 🌐 API & Integration
- **[API Documentation](api-documentation.md)** - Complete REST API reference
- **[Security Guide](security-guide.md)** - Security implementation and best practices

## 📖 Document Purpose & Audience

| Document | Audience | Purpose | Read Time |
|----------|----------|---------|-----------|
| **Developer Onboarding** | New developers | Complete setup and first contribution | 2-3 hours |
| **Architecture Guide** | Senior developers, architects | Understanding system design | 1-2 hours |
| **Domain Model Guide** | All developers | Business logic and domain patterns | 1 hour |
| **API Documentation** | Frontend developers, integrators | API contracts and usage | 30 min |
| **Security Guide** | Security-conscious developers | Security implementation details | 1 hour |
| **Code Analysis Report** | Tech leads, architects | Quality assessment and improvements | 20 min |

## 🎯 Getting Started Paths

### New Developer Path
1. **[Developer Onboarding](developer-onboarding.md)** - Start here for complete setup
2. **[Architecture Guide](architecture-guide.md)** - Understand the system design
3. **[Domain Model Guide](domain-model-guide.md)** - Learn the business logic
4. **[API Documentation](api-documentation.md)** - Reference for development

### Frontend Developer Path
1. **[API Documentation](api-documentation.md)** - API contracts and examples
2. **[Security Guide](security-guide.md)** - Authentication and authorization
3. **[Developer Onboarding](developer-onboarding.md)** - Local setup (if needed)

### Architect/Tech Lead Path
1. **[Code Analysis Report](code-analysis-report.md)** - Current system assessment
2. **[Architecture Guide](architecture-guide.md)** - Detailed architectural patterns
3. **[Domain Model Guide](domain-model-guide.md)** - Business domain structure
4. **[Security Guide](security-guide.md)** - Security implementation review

### Security Specialist Path
1. **[Security Guide](security-guide.md)** - Complete security implementation
2. **[Code Analysis Report](code-analysis-report.md)** - Security assessment findings
3. **[API Documentation](api-documentation.md)** - Endpoint security details

## 🎓 Learning Resources

- **📚 Study Notes**: [Comprehensive learning documentation](https://newkayak12.github.io/2025/05/09/rollup-2025-01.firstHalf.html)
- **📋 Project Planning**: [GitHub Project Boards](https://github.com/users/newkayak12/projects)
- **📖 Wiki**: [Detailed technical documentation](https://github.com/newkayak12/prototype-reservation-system/wiki)
- **🎨 Event Storming**: [Domain modeling visualization](../img/eventStorming.excalidraw.svg)

## 🔄 Documentation Maintenance

### Update Schedule
- **API Documentation**: Updated with each API change
- **Architecture Guide**: Updated with major architectural changes
- **Domain Model Guide**: Updated with business logic changes
- **Security Guide**: Updated with security enhancements
- **Developer Onboarding**: Reviewed monthly for accuracy
- **Code Analysis Report**: Generated quarterly or on major releases

### Contributing to Documentation
1. **Follow the established format** and structure
2. **Include code examples** where relevant
3. **Update related documents** when making changes
4. **Test all commands and examples** before publishing
5. **Use clear, concise language** appropriate for the audience

### Documentation Standards
- **Markdown format** for all documentation
- **Code blocks** with proper syntax highlighting
- **Mermaid diagrams** for visual representations
- **Table of contents** for long documents
- **Cross-references** between related documents

## 🛠️ Tools & Resources

### Documentation Tools
- **Markdown editors**: Typora, Mark Text, or IDE plugins
- **Diagram tools**: Mermaid.js for flowcharts and diagrams
- **API tools**: Postman for API testing and examples
- **Version control**: Git for tracking documentation changes

### External References
- **Spring Boot Documentation**: https://docs.spring.io/spring-boot/docs/current/reference/html/
- **Kotlin Language Guide**: https://kotlinlang.org/docs/
- **Hexagonal Architecture**: https://alistair.cockburn.us/hexagonal-architecture/
- **Domain-Driven Design**: https://martinfowler.com/tags/domain%20driven%20design.html

## 📞 Support & Feedback

### Getting Help
1. **Check existing documentation** first
2. **Search code examples** in the codebase
3. **Ask team members** for clarification
4. **Create issues** for documentation improvements

### Feedback & Improvements
- **Report inaccuracies** or outdated information
- **Suggest improvements** for clarity or completeness
- **Share best practices** discovered during development
- **Contribute examples** and use cases

---

**🏆 This project demonstrates enterprise-grade software engineering practices with a focus on maintainability, scalability, and code quality.**

**Last Updated**: January 2025  
**Documentation Version**: 1.0  
**Project Version**: 0.0.1-SNAPSHOT