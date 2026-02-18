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
-   **`SectorRotationTracker`:** Tracks industry sector performance from FinViz. Fetches daily performance data, sends reports on top/bottom performers, and triggers rotation analysis.
-   **`SectorRotationAnalyzer`:** Statistical analysis component that detects sector rotation signals using Z-Score analysis. Calculates historical mean/standard deviation and identifies sectors with >2σ deviation.
-   **`RelativeStrengthTracker`:** Tracks stock performance relative to SPY benchmark using 50-period EMA crossover detection.
-   **`TelegramClient` & `TelegramMessageProcessor`:** Handle all Telegram Bot API interactions, from sending alerts to processing user commands via the command dispatcher pattern.
-   **`TelegramCommandDispatcher`:** Routes incoming commands to appropriate processors using the Command pattern. Easily extensible for new commands.
-   **`RsiCommandProcessor`**: Handles the `/rsi` command from Telegram, allowing users to get current RSI values for any symbol.
-   **`TargetPriceProvider`:** Manages the watchlist of symbols to be monitored, including those to be ignored. Allows dynamic configuration.
-   **`RootErrorHandler`:** Centralized error handler wrapping all scheduled tasks. Ensures that failures in one task don't bring down the entire application.

## External API Clients

-   **`FinnhubClient`:** Interacts with Finnhub API for stock prices and insider transactions.
-   **`CoinGeckoClient`:** Interacts with CoinGecko API for cryptocurrency prices.
-   **`FinvizClient`:** Web scraper using JSoup to fetch industry performance data from FinViz. No API key required.

## Data Persistence Components

-   **`TargetPriceProvider`:** JSON-based storage for buy/sell target prices.
-   **`StockSymbolRegistry`:** Dynamic stock symbol management with JSON persistence.
-   **`InsiderPersistence`:** Stores historical insider transaction data.
-   **`SectorPerformancePersistence`:** Stores daily sector performance snapshots for trend analysis.
-   **`TelegramMessageTracker`:** Tracks last processed message ID to avoid duplicates.
-   **`SqlitePriceQuoteRepository`:** **NEW** - SQLite-based storage for historical Finnhub price quotes.

## Design Patterns

-   **Repository Pattern (NEW):** The data persistence layer uses the Repository pattern with interface-implementation separation. `PriceQuoteRepository` defines the contract, `SqlitePriceQuoteRepository` provides SQLite implementation. This allows easy swapping of database backends.
-   **Command Pattern**: The Telegram command processing framework exemplifies the Command pattern. Each command (`/add`, `/remove`, `/rsi`, `/show`, etc.) is encapsulated in its own class (`RsiCommand`, `AddCommand`, etc.) with a corresponding processor (`RsiCommandProcessor`, `AddCommandProcessor`, etc.). The `TelegramCommandDispatcher` routes commands to appropriate processors via the `canProcess()` method.
-   **Dependency Injection**: Used extensively by Spring to manage component dependencies, promoting loose coupling and testability. All major components are injected via constructor injection.
-   **Scheduler Pattern**: The `Scheduler` component uses Spring's `@Scheduled` annotation to run tasks at fixed intervals. Separate schedulers exist for `stockMarketMonitoring`, `cryptoMarketMonitoring`, `dailyRsiFetching`, `weeklyInsiderReporting`, `telegramMessagePolling`, and `dailySectorRotationTracking`.
-   **Strategy Pattern**: Different `PriceEvaluator` implementations for different data sources (`FinnhubPriceEvaluator`, `CoinGeckoPriceEvaluator`) demonstrate the Strategy pattern. This allows price evaluation logic to be easily swapped or extended.
-   **Caching Pattern**: Price evaluators maintain `lastPriceCache` maps to store recently fetched prices. The `RsiService` leverages these caches via `getCurrentPriceFromCache()` for real-time RSI calculations.
-   **Facade Pattern**: The `TelegramClient` serves as a facade, simplifying interaction with the complex underlying Telegram Bot API.
-   **Template Method Pattern**: The `BasePriceEvaluator` abstract class provides common price evaluation logic, with specific implementations in `FinnhubPriceEvaluator` and `CoinGeckoPriceEvaluator`.
-   **Singleton Pattern**: Spring beans are singletons by default, ensuring single instances of each component throughout the application lifecycle.

## Scheduled Tasks

| Task | Schedule | Zone | Description |
|------|----------|------|-------------|
| `stockMarketMonitoring` | Every 5 min (9:30-16:00, Mon-Fri) | America/New_York | Stock price monitoring + **SQLite storage** |
| `cryptoMarketMonitoring` | Every 5 min | UTC | 24/7 crypto monitoring |
| `rsiStockMonitoring` | Daily 16:30 (Mon-Fri) | America/New_York | Stock RSI + RS analysis |
| `rsiCryptoMonitoring` | Daily 00:05 | America/New_York | Crypto RSI calculations |
| `weeklyInsiderTradingReport` | Weekly Fri 17:00 | America/New_York | Insider transactions |
| `monthlyApiUsageReport` | Monthly 1st, 00:30 | UTC | API usage statistics |
| `dailySectorRotationTracking` | Daily 22:30 (Mon-Fri) | America/New_York | Sector performance + rotation alerts |
| `telegramMessagePolling` | Every 5 seconds | UTC | Process Telegram commands |

## Component Relationships

```
Scheduler
├── FinnhubPriceEvaluator → FinnhubClient → Finnhub API
│   └── SqlitePriceQuoteRepository → SQLite DB (NEW)
├── CoinGeckoPriceEvaluator → CoinGeckoClient → CoinGecko API
├── RsiService → RsiPriceFetcher → Price APIs
│   └── RelativeStrengthTracker → RelativeStrengthService
├── InsiderTracker → InsiderPersistence → JSON file
├── SectorRotationTracker → FinvizClient → FinViz website
│   ├── SectorPerformancePersistence → JSON file
│   └── SectorRotationAnalyzer
└── TelegramMessageProcessor → TelegramClient → Telegram API
    └── TelegramCommandDispatcher
        ├── AddCommandProcessor
        ├── RemoveCommandProcessor
        ├── SetCommandProcessor
        ├── ShowCommandProcessor
        └── RsiCommandProcessor
```

## SQLite Repository Pattern (NEW)

The `SqlitePriceQuoteRepository` implements the Repository pattern for price quote persistence:

### Interface Definition
```java
public interface PriceQuoteRepository {
    void save(PriceQuoteResponse priceQuote);
    List<PriceQuoteEntity> findBySymbol(String symbol);
    List<PriceQuoteEntity> findBySymbolAndDate(String symbol, LocalDate date);
    List<PriceQuoteEntity> findBySymbolAndDateRange(String symbol, LocalDate startDate, LocalDate endDate);
}
```

### Auto-Initialization Pattern
```java
public SqlitePriceQuoteRepository(DataSource dataSource) {
    this.dataSource = dataSource;
    initializeSchema();  // Creates table and indexes on construction
}

private void initializeSchema() {
    String createTableSql = """
        CREATE TABLE IF NOT EXISTS finnhub_price_quotes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            symbol TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            ...
            UNIQUE(symbol, timestamp)
        )
        """;
    // Execute DDL statements
}
```

### UTC Timestamp Best Practice
```java
// Storage: Use UTC epoch seconds
long timestamp = Instant.now().getEpochSecond();

// Query: Convert dates using UTC timezone
long startOfDay = date.atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
```

### Integration with FinnhubPriceEvaluator
```java
@Override
protected void processValidPriceQuote(PriceQuoteResponse priceQuote) {
    // Store in SQLite for historical analysis
    priceQuoteRepository.save(priceQuote);
    
    // Existing price evaluation logic
    super.processValidPriceQuote(priceQuote);
}
```

**Benefits:**
- Historical data for technical analysis
- Foundation for future indicators (MACD, Bollinger Bands)
- Enables backtesting capabilities
- Clear separation of concerns (interface vs implementation)

## Web Scraping Pattern (FinViz)

The `FinvizClient` uses JSoup for HTML parsing instead of browser automation:

```java
Document doc = Jsoup.connect(FINVIZ_URL)
    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)...")
    .timeout(10000)
    .get();

Elements rows = doc.select("table.table-light tr");
for (Element row : rows) {
    Elements cells = row.select("td");
    // Parse industry name and performance metrics
}
```

**Advantages:**
- No browser dependencies (Playwright/Selenium)
- Faster execution
- Lower resource consumption
- Simpler deployment

## JSON Persistence Pattern

All configuration data uses Jackson ObjectMapper for JSON serialization:

```java
// Reading
List<T> items = objectMapper.readValue(file, 
    new TypeReference<List<T>>() {});

// Writing
objectMapper.writerWithDefaultPrettyPrinter()
    .writeValue(file, items);
```

## Error Handling Pattern

The `RootErrorHandler` wraps all scheduled tasks:

```java
@Scheduled(...)
public void someTask() {
    rootErrorHandler.run(component::method);
}
```

This ensures failures are logged but don't crash the application.

## Z-Score Statistical Analysis Pattern (Sector Rotation)

The `SectorRotationAnalyzer` uses Z-Score analysis for early rotation detection:

```java
// Calculate z-score: (current_value - mean) / std_dev
double zScoreWeekly = calculateZScore(current.perfWeek(), stats.weeklyMean, stats.weeklyStdDev);
double zScoreMonthly = calculateZScore(current.perfMonth(), stats.monthlyMean, stats.monthlyStdDev);

// Only generate HIGH confidence signals (>2σ)
if (absZWeekly >= 2.0 && absZMonthly >= 2.0) {
    // Both weekly and monthly significantly deviate from historical norm
    // Requires same direction (both positive or both negative)
}
```

**Algorithm Properties:**
- **Adaptive Thresholds**: Z-scores auto-adjust to market volatility
- **Conservative Alerts**: Only HIGH confidence signals (>2σ) to minimize false positives
- **Dual Timeframe**: Requires both weekly AND monthly confirmation
- **Same-Direction Requirement**: Prevents false positives from diverging signals
- **Minimum History**: Requires 5+ snapshots for reliable statistics

## Testing Patterns

- **Mock-based testing**: All external dependencies are mocked using Mockito
- **Argument Captors**: Used to verify complex method arguments
- **Temp Files**: `@TempDir` for testing file persistence
- **In-Memory SQLite**: **NEW** Unique temp DB files per test with UUID naming
- **WireMock**: For HTTP client testing (optional)