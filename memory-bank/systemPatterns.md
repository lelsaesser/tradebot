# System Patterns

This document describes the system architecture, key technical decisions, design patterns, component relationships, and critical implementation paths.

## Architecture

The application follows a modular, component-based architecture built on the Spring Framework. The core logic is orchestrated by a central `Scheduler` component that triggers various tasks at scheduled intervals. These tasks are designed to be self-contained and resilient, with a `RootErrorHandler` providing a safety net for unhandled exceptions.

## Key Components

-   **`Scheduler`:** The heart of the application, responsible for orchestrating all scheduled tasks. It has been updated to include separate, independently configurable schedulers for stock and crypto monitoring, allowing for more granular control over polling intervals.
-   **`*PriceEvaluator`:** A set of components (`FinnhubPriceEvaluator`, `CoinGeckoPriceEvaluator`) responsible for fetching and evaluating prices from different APIs. This design supports multiple data sources and can be extended to include others.
-   **`RsiPriceFetcher`:** A component dedicated to fetching historical price data for RSI calculations. This is a critical component for technical analysis.
-   **`InsiderTracker`:** A component for tracking and reporting insider trading activities. This provides valuable market insights.
-   **`TelegramClient` & `TelegramMessageProcessor`:** These components handle all interactions with the Telegram Bot API, from sending alerts to processing user commands.
-   **`TargetPriceProvider`:** Manages the list of symbols to be monitored, including those to be ignored. This allows for dynamic configuration of the bot's watchlist.
-   **`RootErrorHandler`:** A centralized error handler that wraps all scheduled tasks. This ensures that a failure in one task does not bring down the entire application.
-   **`RsiCommandProcessor`**: A component that handles the `/rsi` command from Telegram, which allows users to get the current RSI value for a given symbol.

## Design Patterns

-   **Command Pattern**: The Telegram command processing framework is a good example of the Command pattern. Each command (`/add`, `/remove`, `/rsi`, etc.) is encapsulated in its own class, and a dispatcher (`TelegramCommandDispatcher`) is responsible for routing the command to the appropriate processor.
-   **Dependency Injection:** Used extensively by Spring to manage component dependencies, promoting loose coupling and testability.
-   **Scheduler Pattern:** The `Scheduler` component uses the `@Scheduled` annotation to run tasks at fixed intervals or cron-based schedules. The recent refactoring split a single market monitoring job into `stockMarketMonitoring` and `cryptoMarketMonitoring`, each with its own schedule.
-   **Strategy Pattern:** The use of different `PriceEvaluator` implementations for different data sources (e.g., `FinnhubPriceEvaluator`, `CoinGeckoPriceEvaluator`) is a clear example of the Strategy pattern. This allows the price evaluation logic to be easily swapped or extended.
-   **Facade Pattern:** The `TelegramClient` can be seen as a facade that simplifies interaction with the more complex underlying Telegram Bot API.
-   **Singleton Pattern:** Spring beans are singletons by default, ensuring that there is only one instance of each component, which is appropriate for this application's architecture.
