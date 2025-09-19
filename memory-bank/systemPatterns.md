# System Patterns

This document describes the system architecture, key technical decisions, design patterns, component relationships, and critical implementation paths.

## Architecture
The application follows a modular, component-based architecture built on the Spring Framework. The core logic is orchestrated by a central `Scheduler` component that triggers various tasks at scheduled intervals.

## Key Components
- **`Scheduler`:** The heart of the application, responsible for orchestrating all scheduled tasks.
- **`*PriceEvaluator`:** A set of components (`FinnhubPriceEvaluator`, `CoinGeckoPriceEvaluator`) responsible for fetching and evaluating prices from different APIs.
- **`RsiPriceFetcher`:** A component dedicated to fetching historical price data for RSI calculations.
- **`InsiderTracker`:** A component for tracking and reporting insider trading activities.
- **`TelegramClient` & `TelegramMessageProcessor`:** Components for interacting with the Telegram Bot API.
- **`TargetPriceProvider`:** Manages the list of symbols to be monitored, including those to be ignored.
- **`RootErrorHandler`:** A centralized error handler to wrap scheduled tasks and ensure graceful error handling.

## Design Patterns
- **Dependency Injection:** Used extensively by Spring to manage component dependencies.
- **Scheduler Pattern:** The `Scheduler` component uses the `@Scheduled` annotation to run tasks at fixed intervals or cron-based schedules.
- **Strategy Pattern:** The use of different `PriceEvaluator` implementations for different data sources (Finnhub, CoinGecko) can be seen as a form of the Strategy pattern.
