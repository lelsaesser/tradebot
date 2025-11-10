# Tech Context

This document covers the technologies used, development setup, technical constraints, dependencies, and tool usage patterns.

## Technologies
- **Language:** Java 23
- **Framework:** Spring Boot 3.5.7
- **Build Tool:** Maven 

## Dependencies
- **Spring Boot Starters:** 
  - `spring-boot-starter`: Core Spring Boot functionality
  - `spring-boot-starter-web`: Web application support with embedded Tomcat
  - `spring-boot-starter-validation`: Bean validation with Hibernate validator
  - `spring-boot-starter-test`: Testing support (test scope)
- **Lombok 1.18.34:** Reduces boilerplate code with annotations like `@Slf4j`, `@RequiredArgsConstructor`, `@Getter`, etc.
- **Jackson:** JSON serialization/deserialization with JSR-310 (Java Time) datatype support
- **Testing:** 
  - JUnit Jupiter 6.0.1
  - Mockito 5.20.0 (core and junit-jupiter integration)
  - Hamcrest 3.0
  - Spring Boot Test support

## Build Configuration
- **Compiler:** `maven-compiler-plugin` 3.14.1 configured for Java 23 with Lombok annotation processing
- **Packaging:** `spring-boot-maven-plugin` for creating executable JARs
- **Code Coverage:** `jacoco-maven-plugin` 0.8.14 enforces 99% instruction coverage ratio (increased from 98%)
  - Generates coverage reports during verify phase
  - Fails build if coverage falls below 99%
- **Code Formatting:** `spotless-maven-plugin` 3.0.0 with Google Java Format 1.30.0 (AOSP style)
  - Enforces consistent code style across the project
  - Runs check during build to ensure compliance

## Development Patterns
- **Annotation-Driven Configuration:** Uses Spring annotations like `@Service`, `@Component`, `@Scheduled`, `@Autowired`
- **Constructor Injection:** Preferred DI method using Lombok's `@RequiredArgsConstructor`
- **Logging:** SLF4J via Lombok's `@Slf4j` annotation
- **Scheduled Tasks:** Spring's `@Scheduled` annotation with fixed delays (in milliseconds)
- **JSON Persistence:** Uses Jackson ObjectMapper to persist data structures to JSON files in the `config/` directory
