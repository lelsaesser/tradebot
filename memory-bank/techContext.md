# Tech Context

This document covers the technologies used, development setup, technical constraints, dependencies, and tool usage patterns.

## Technologies
- **Language:** Java 23
- **Framework:** Spring Boot 3.5.5
- **Build Tool:** Maven

## Dependencies
- **Spring Boot Starters:** web, validation, test
- **Lombok:** For reducing boilerplate code.
- **Jackson:** For JSON serialization and deserialization.
- **Testing:** JUnit 5, Mockito, Hamcrest

## Build Configuration
- **Compiler:** `maven-compiler-plugin` configured for Java 23.
- **Packaging:** `spring-boot-maven-plugin` for creating executable JARs.
- **Code Coverage:** `jacoco-maven-plugin` is used to enforce a 98% instruction coverage ratio.
