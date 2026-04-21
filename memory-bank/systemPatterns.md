# System Patterns

This document describes the system architecture, key technical decisions, design patterns, component relationships, and critical implementation paths.

## Architecture

The application follows a modular, component-based architecture built on the Spring Framework. The core logic is orchestrated by a central `Scheduler` component that triggers various tasks at scheduled intervals. These tasks are designed to be self-contained and resilient, with a `RootErrorHandler` providing a safety net for unhandled exceptions.

## Key Components

-   **`Scheduler`:** The heart of the application, orchestrating all scheduled tasks. Includes separate schedulers for stock and crypto monitoring with independent polling intervals.
-   **`SymbolRegistry`:** Unified `@Service` bean that owns all tracked symbols — ETFs (sector, thematic, benchmark as hardcoded constants) and individual stocks (JSON-loaded, dynamically add/remove via Telegram). Replaces the former split between `SectorEtfRegistry` (static) and `StockSymbolRegistry` (service). Methods: `getAll()`, `getAllEtfs()`, `getBroadSectorEtfs()`, `getThematicEtfs()`, `getStocks()`, `isEtf()`, `isSectorEtf()`, `fromString()`, `addSymbol()`, `removeSymbol()`.
-   **`DailyPriceProvider`:** Unified data access layer for daily closing prices. Tries OHLCV (Twelve Data) first, falls back to Finnhub. Same `findDailyClosingPrices(symbol, days)` signature. Used by EmaService, BollingerBandService, RelativeStrengthService, MomentumRocService.
-   **`*PriceEvaluator`:** A set of components (`FinnhubPriceEvaluator`, `CoinGeckoPriceEvaluator`) responsible for fetching and evaluating prices from different APIs.
-   **`RsiService`:** Core service for RSI calculations. Manages historical price data, calculates RSI values, detects market holidays.
-   **`RsiPriceFetcher`:** Dedicated component for fetching historical price data for RSI calculations.
-   **`InsiderTracker`:** Tracks and reports insider trading activities.
-   **`SectorRotationTracker`:** Tracks industry sector performance from FinViz.
-   **`SectorRotationAnalyzer`:** Statistical analysis component that detects sector rotation signals using Z-Score analysis.
-   **`RelativeStrengthTracker`:** Tracks stock performance relative to SPY benchmark using 50-period EMA crossover detection.
-   **`SectorRelativeStrengthTracker`:** Monitors sector ETF performance vs SPY benchmark. Real-time RS crossover alerts + daily summary. Uses `SymbolRegistry.getAllEtfs()` and `getThematicSymbols()` for report section splitting.
-   **`MomentumRocService`:** Calculates Rate of Change (ROC) momentum and detects zero-line crossovers.
-   **`SectorMomentumRocTracker`:** Real-time sector ETF momentum analysis using ROC10/ROC20 values.
-   **`TailRiskService`:** Calculates excess kurtosis and skewness from daily price changes to detect fat tail risk.
-   **`TailRiskTracker`:** Monitors sector ETFs for elevated tail risk with directional context.
-   **`StatisticsUtil`:** Shared utility class providing `mean()`, `standardDeviation()`, `zScore()`, `percentileRank()`, `populationStdDev()`, `percentile()`, `calculateEma()`, `calculateRocValue()`, and `roundTo2Decimals()`.
-   **`BollingerBandService`:** Calculates Bollinger Bands (20-period SMA ± 2σ), %B positioning, bandwidth, and bandwidth percentile for squeeze detection.
-   **`BollingerBandTracker`:** Orchestrates Bollinger Band analysis across all sector ETFs and tracked stocks. Uses `SymbolRegistry.getAllEtfs()` for sectors and `SymbolRegistry.getStocks()` for individual stocks.
-   **`EmaService`:** Calculates EMAs (9, 21, 50, 100, 200 day) for a symbol. Calculates every EMA for which enough data exists (minimum 9 data points).
-   **`EmaTracker`:** Orchestrates daily EMA report across all tracked stocks via `SymbolRegistry.getAll()`.
-   **`VfiService`:** Calculates Volume Flow Indicator from OHLCV data (130-day lookback, 5-period signal line EMA). Returns `Optional<VfiAnalysis>`.
-   **`VfiTracker`:** Orchestrates daily RS+VFI combined report. Iterates `SymbolRegistry.getAll()`, classifies each symbol as GREEN (RS↑ + VFI↑), YELLOW (mixed), or RED (RS↓ + VFI↓) via `CombinedSignalType`. Sends via `TelegramGateway.sendMessage()` at 9:00 CET pre-market.
-   **`PullbackBuyTracker`:** Real-time EMA pullback buy alerts. Runs every 5 min during market hours (inside `stockMarketMonitoring`). Detects stocks below EMA 9/21 but above EMA 50/100/200, with positive RS and VFI. Reads live prices from `FinnhubPriceEvaluator.lastPriceCache` (no separate API calls). Per-stock Telegram alerts with 8-hour cooldown via `IgnoreReason.PULLBACK_BUY_ALERT`.
-   **`OhlcvFetcher`:** Fetches daily OHLCV data from Twelve Data for all tracked symbols (ETFs + stocks via `SymbolRegistry.getAll()`). Backfill: 400 data points. Refresh: 5 data points. 8-second delay between requests.
-   **`TelegramClient` / `LocalTelegramGateway` / `TelegramMessageProcessor`:** Telegram integration is profile-aware. All sending components inject `TelegramGateway` interface.
-   **`TelegramCommandDispatcher`:** Routes incoming commands to appropriate processors using the Command pattern.
-   **`TargetPriceProvider`:** Manages the watchlist of symbols to be monitored.
-   **`RootErrorHandler`:** Centralized error handler wrapping all scheduled tasks. `run()` preserves fire-and-log; `runWithStatus()` adds boolean success/failure for dev triggers.
-   **`DevDataSeeder`:** `dev`-only startup seeder that populates SQLite quote history, OHLCV data (400 days), RSI/RS/ROC state.
-   **`DevJobController`:** `dev`-only manual trigger surface. 14 individual endpoints + 1 composite `run-all` endpoint. Individual endpoints return HTTP 200/500. `run-all` orchestrates 4-phase smoke test execution.

## External API Clients

-   **`FinnhubClient`:** Stock prices and insider transactions. API key required.
-   **`CoinGeckoClient`:** Cryptocurrency prices. No auth.
-   **`FinvizClient`:** Web scraper using JSoup for industry performance. No auth.
-   **`TwelveDataClient`:** Fetches daily OHLCV data. API key required. 8 req/min rate limit. Metered via `ApiRequestMeteringService`.

## Data Persistence Components

-   **`SqlitePriceQuoteRepository`:** Historical Finnhub price quotes. Used by TailRiskService (for `findDailyChangePercents()`) and as fallback for DailyPriceProvider.
-   **`SqliteMomentumRocRepository`:** Momentum ROC state (previous ROC values for crossover detection).
-   **`SqliteOhlcvRepository`:** Twelve Data daily OHLCV data in `twelvedata_daily_ohlcv` table. Primary source for DailyPriceProvider and VfiService.
-   **`FeatureToggleService`:** Runtime feature flag management with JSON persistence and caching.

## Design Patterns

-   **Repository Pattern:** Interface-implementation separation. `PriceQuoteRepository`, `OhlcvRepository`, `MomentumRocRepository`.
-   **Command Pattern**: Telegram command processing (`/add`, `/remove`, `/rsi`, `/show`, `/set`).
-   **Dependency Injection**: Constructor injection via `@RequiredArgsConstructor`. All major components injected.
-   **Profile Gating**: Default = production. `dev` = opt-in local profile.
-   **Scheduler Pattern**: `@Scheduled` with cron expressions and timezone support.
-   **Strategy Pattern**: Different `PriceEvaluator` implementations for different data sources.
-   **Facade Pattern**: `TelegramClient` simplifies Telegram Bot API interaction.
-   **Data Source Fallback**: `DailyPriceProvider` tries OHLCV first, falls back to Finnhub.
-   **Per-Reason TTL**: `IgnoreReason` enum carries `ttlSeconds` per value. `TargetPriceProvider.isSymbolIgnored()` uses `reason.getTtlSeconds()` instead of a global constant. Existing alerts use 12h, pullback uses 8h.
-   **Finnhub as Data Ingestion Layer**: `FinnhubPriceEvaluator` fetches live prices and populates `lastPriceCache`. All downstream indicators/trackers read from the cache or SQLite — never make their own Finnhub API calls.
-   **Phased Smoke Test**: `DevJobController.runAll()` executes 14 jobs in 4 dependency-ordered phases (seed → OHLCV fetch → parallel independents → VFI). Returns aggregate pass/fail with per-job results. `RootErrorHandler.runWithStatus()` provides boolean success/failure without propagating exceptions.

## Scheduled Tasks

| Task | Schedule | Zone | Description |
|------|----------|------|-------------|
| `stockMarketMonitoring` | Every 5 min (9:30-16:00, Mon-Fri) | America/New_York | Stock prices + RS alerts + ROC alerts + pullback buy alerts |
| `hourlySignalMonitoring` | Every 60 min (9:30-16:00, Mon-Fri) | America/New_York | BB + RSI reports (delete previous, send new) |
| `cryptoMarketMonitoring` | Every 7 min | UTC | 24/7 crypto monitoring |
| `rsiStockMonitoring` | Daily 23:00 (Mon-Fri) | CET | Stock RSI + RS analysis |
| `rsiCryptoMonitoring` | Daily 00:00 | America/New_York | Crypto RSI calculations |
| `dailyVfiReport` | Daily 09:00 (Mon-Fri) | CET | Combined RS+VFI report (pre-market) |
| `dailySectorRelativeStrengthReport` | Daily 16:00,21:00 (Mon-Fri) | CET | Sector RS vs SPY summary |
| `dailyTailRiskMonitoring` | Daily 13:00 (Mon-Fri) | CET | Tail risk kurtosis + skewness alerts |
| `dailyBollingerBandReport` | Daily 15:40 (Mon-Fri) | CET | Bollinger Band daily summary |
| `dailyEmaReport` | Daily 15:50 (Mon-Fri) | CET | EMA classification report |
| `dailyOhlcvFetch` | Daily 22:30 (Mon-Fri) | CET | Twelve Data OHLCV fetch (400 data points) |
| `weeklyInsiderTradingReport` | Weekly Sat 12:00 | CET | Insider transactions |
| `monthlyApiUsageReport` | Monthly 1st, 00:00 | UTC | API usage statistics |
| `telegramMessagePolling` | Every 60 seconds | UTC | Process Telegram commands |

## Component Relationships

```
Scheduler
├── FinnhubPriceEvaluator → FinnhubClient → Finnhub API
│   └── SqlitePriceQuoteRepository → SQLite DB
├── CoinGeckoPriceEvaluator → CoinGeckoClient → CoinGecko API
├── RsiService → RsiPriceFetcher → Price APIs
│   └── RelativeStrengthTracker → RelativeStrengthService
├── SymbolRegistry (unified symbol source)
│   ├── ETF constants: BROAD_SECTOR_ETFS, THEMATIC_ETFS, BENCHMARK
│   ├── JSON stocks: config/stock-symbols.json
│   └── API: getAll(), getAllEtfs(), getStocks(), isEtf(), fromString()
├── DailyPriceProvider (OHLCV-first, Finnhub-fallback)
│   ├── OhlcvRepository → SQLite (twelvedata_daily_ohlcv)
│   └── PriceQuoteRepository → SQLite (finnhub_price_quotes)
├── SectorRelativeStrengthTracker → RelativeStrengthService + SymbolRegistry
├── SectorMomentumRocTracker → MomentumRocService + SymbolRegistry
├── TailRiskTracker → TailRiskService + SymbolRegistry
├── BollingerBandTracker → BollingerBandService + SymbolRegistry
├── EmaTracker → EmaService + SymbolRegistry
├── VfiTracker → VfiService + RelativeStrengthService + SymbolRegistry
│   └── CombinedSignalType: GREEN/YELLOW/RED classification
├── PullbackBuyTracker → EmaService + RelativeStrengthService + VfiService + FinnhubPriceEvaluator (cache) + SymbolRegistry
│   └── Per-stock alerts with 8h cooldown via IgnoreReason.PULLBACK_BUY_ALERT
├── OhlcvFetcher → TwelveDataClient + SymbolRegistry
├── StatisticsUtil (shared math: mean, stddev, EMA, ROC, zScore)
├── InsiderTracker → InsiderPersistence
├── SectorRotationTracker → FinvizClient + SectorRotationAnalyzer
├── RootErrorHandler (run + runWithStatus)
├── DevDataSeeder (dev) → seeds SQLite + JSON + OHLCV
├── DevJobController (dev) → manual job endpoints
└── TelegramMessageProcessor → TelegramGateway
    ├── TelegramClient (default / non-dev)
    ├── LocalTelegramGateway (dev)
    └── TelegramCommandDispatcher
```

## Delete-Before-Send Pattern (Telegram)

For recurring hourly reports (BB, RSI), the bot deletes the previous message before sending the new one:
- In-memory `lastTelegramReportMessageId` tracking
- `sendMessageAndReturnId()` returns `OptionalLong`
- Delete failures logged but don't block new message delivery

Daily reports (VFI, EMA, Tail Risk, BB daily) use regular `sendMessage()` — each day's report stays in chat.

## Testing Patterns

- **Mock-based testing**: External dependencies mocked with Mockito
- **Argument Captors**: For verifying complex method arguments
- **Temp Files**: `@TempDir` for file persistence tests
- **In-Memory SQLite**: Unique temp DB files per test with UUID naming
- **Configurable File Paths**: Constructor injection for file paths in tests
- **Lenient stubs**: Used when SymbolRegistry methods return real ETF data via static constants but only specific symbols are relevant to the test
