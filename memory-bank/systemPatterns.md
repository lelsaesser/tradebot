# System Patterns

This document describes the system architecture, key technical decisions, design patterns, component relationships, and critical implementation paths.

## Architecture

The application follows a modular, component-based architecture built on the Spring Framework. The core logic is orchestrated by a central `Scheduler` component that triggers various tasks at scheduled intervals. These tasks are designed to be self-contained and resilient, with a `RootErrorHandler` providing a safety net for unhandled exceptions.

## Key Components

-   **`Scheduler`:** The heart of the application, orchestrating all scheduled tasks. Includes separate schedulers for stock and crypto monitoring with independent polling intervals.
-   **`*PriceEvaluator`:** A set of components (`FinnhubPriceEvaluator`, `CoinGeckoPriceEvaluator`) responsible for fetching and evaluating prices from different APIs. Maintains in-memory caches of last fetched prices for real-time RSI calculations. This design supports multiple data sources and can be extended.
-   **`RsiService`:** Core service for RSI calculations. Manages historical price data, calculates RSI values, detects market holidays, and sends Telegram notifications for overbought/oversold conditions. Integrates cached current prices for accurate on-demand RSI queries.
-   **`RsiPriceFetcher`:** Dedicated component for fetching historical price data for RSI calculations. Critical for technical analysis.
-   **`InsiderTracker`:** Tracks and reports insider trading activities, providing valuable market insights.
-   **`TelegramClient` & `TelegramMessageProcessor`:** Handle all Telegram Bot API interactions, from sending alerts to processing user commands via the command dispatcher pattern.
-   **`TelegramCommandDispatcher`:** Routes incoming commands to appropriate processors using the Command pattern. Easily extensible for new commands.
-   **`RsiCommandProcessor`**: Handles the `/rsi` command from Telegram, allowing users to get current RSI values for any symbol.
-   **`TargetPriceProvider`:** Manages the watchlist of symbols to be monitored, including those to be ignored. Allows dynamic configuration.
-   **`RootErrorHandler`:** Centralized error handler wrapping all scheduled tasks. Ensures that failures in one task don't bring down the entire application.

## Design Patterns

-   **Command Pattern**: The Telegram command processing framework exemplifies the Command pattern. Each command (`/add`, `/remove`, `/rsi`, `/show`, etc.) is encapsulated in its own class (`RsiCommand`, `AddCommand`, etc.) with a corresponding processor (`RsiCommandProcessor`, `AddCommandProcessor`, etc.). The `TelegramCommandDispatcher` routes commands to appropriate processors via the `canProcess()` method.
-   **Dependency Injection**: Used extensively by Spring to manage component dependencies, promoting loose coupling and testability. All major components are injected via constructor injection.
-   **Scheduler Pattern**: The `Scheduler` component uses Spring's `@Scheduled` annotation to run tasks at fixed intervals. Separate schedulers exist for `stockMarketMonitoring`, `cryptoMarketMonitoring`, `dailyRsiFetching`, `weeklyInsiderReporting`, and `telegramMessagePolling`.
-   **Strategy Pattern**: Different `PriceEvaluator` implementations for different data sources (`FinnhubPriceEvaluator`, `CoinGeckoPriceEvaluator`) demonstrate the Strategy pattern. This allows price evaluation logic to be easily swapped or extended.
-   **Caching Pattern**: Price evaluators maintain `lastPriceCache` maps to store recently fetched prices. The `RsiService` leverages these caches via `getCurrentPriceFromCache()` for real-time RSI calculations.
-   **Facade Pattern**: The `TelegramClient` serves as a facade, simplifying interaction with the complex underlying Telegram Bot API.
-   **Template Method Pattern**: The `BasePriceEvaluator` abstract class provides common price evaluation logic, with specific implementations in `FinnhubPriceEvaluator` and `CoinGeckoPriceEvaluator`.
-   **Singleton Pattern**: Spring beans are singletons by default, ensuring single instances of each component throughout the application lifecycle.
