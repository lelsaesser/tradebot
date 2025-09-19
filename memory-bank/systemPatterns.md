# System Patterns

This document describes the system architecture, key technical decisions, design patterns, component relationships, and critical implementation paths.

## Architecture
The application follows a modular, component-based architecture built on the Spring Framework. The core logic is orchestrated by a central `Scheduler` component that triggers various tasks at scheduled intervals.

## Key Components
- **`Scheduler`:** The heart of the application, responsible for orchestrating all scheduled tasks.
- **`*PriceEvaluator`:** A set of components (`FinnhubPriceEvaluator`, `CoinGeckoPriceEvaluator`, `RsiPriceEvaluator`) responsible for fetching and evaluating prices from different APIs.
- **`RsiPriceFetcher`:** A component dedicated to fetching historical price data for RSI calculations.
- **`RsiService`:** Core service for RSI calculations and price data persistence using SQLite database.
- **`DailyPriceRepository`:** JPA repository for price data access with optimized queries.
- **`DataMigrationService`:** Handles automatic migration from JSON files to SQLite database.
- **`InsiderTracker`:** A component for tracking and reporting insider trading activities.
- **`TelegramClient` & `TelegramMessageProcessor`:** Components for interacting with the Telegram Bot API.
- **`TargetPriceProvider`:** Manages the list of symbols to be monitored, including those to be ignored.
- **`RootErrorHandler`:** A centralized error handler to wrap scheduled tasks and ensure graceful error handling.

## Design Patterns
- **Dependency Injection:** Used extensively by Spring to manage component dependencies.
- **Repository Pattern:** JPA repositories provide clean separation between data access and business logic.
- **Service Layer Pattern:** Business logic encapsulated in service components (RsiService, DataMigrationService).
- **Entity Pattern:** JPA entities (DailyPrice) represent persistent data with proper mapping.
- **Scheduler Pattern:** The `Scheduler` component uses the `@Scheduled` annotation to run tasks at fixed intervals or cron-based schedules.
- **Strategy Pattern:** The use of different `PriceEvaluator` implementations for different data sources (Finnhub, CoinGecko) can be seen as a form of the Strategy pattern.
- **Migration Pattern:** Automatic data migration with validation and fallback mechanisms.

## Data Persistence Architecture
- **SQLite Database:** Embedded database for price data storage (`config/tradebot.db`)
- **JPA/Hibernate:** Object-relational mapping with automatic schema generation
- **Custom Queries:** Optimized native SQL queries for performance-critical operations
- **Transaction Management:** Spring-managed transactions for data integrity
- **Indexing Strategy:** Composite indexes on symbol+date for efficient lookups
