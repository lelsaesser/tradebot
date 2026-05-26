# System Patterns

This document describes the system architecture, key technical decisions, design patterns, component relationships, and critical implementation paths.

## Architecture

The application follows a modular, component-based architecture built on the Spring Framework. The core logic is orchestrated by a central `Scheduler` component that triggers various tasks at scheduled intervals. These tasks are designed to be self-contained and resilient, with a `RootErrorHandler` providing a safety net for unhandled exceptions.

## Key Components

-   **`Scheduler`:** The heart of the application, orchestrating all scheduled tasks. Includes separate schedulers for stock and crypto monitoring with independent polling intervals.
-   **`SymbolRegistry`:** Unified `@Service` bean that owns all tracked symbols — ETFs (sector, thematic, benchmark as hardcoded constants) and individual stocks (JSON-loaded, dynamically add/remove via Telegram). Replaces the former split between `SectorEtfRegistry` (static) and `StockSymbolRegistry` (service). Methods: `getAll()`, `getAllEtfs()`, `getBroadSectorEtfs()`, `getThematicEtfs()`, `getStocks()`, `isEtf()`, `isSectorEtf()`, `fromString()`, `addSymbol()`, `removeSymbol()`.
-   **`DailyPriceProvider`:** Unified data access layer for daily closing prices. Tries OHLCV (Twelve Data) first, falls back to Finnhub. Same `findDailyClosingPrices(symbol, days)` signature. Used by EmaService, BollingerBandService, RelativeStrengthService, MomentumRocService. OHLCV reads transparently cached via the `CachingOhlcvRepository` decorator (per-`(symbol, days)` entries, invalidated on every write path).
-   **`*PriceEvaluator`:** A set of components (`FinnhubPriceEvaluator`, `CoinGeckoPriceEvaluator`, `YahooPriceEvaluator`) responsible for fetching and evaluating prices from different APIs. Finnhub and Yahoo evaluators write to the shared `LivePriceCache`.
-   **`LivePriceCache`:** Shared `@Service` bean holding the latest known price per symbol. Internal storage is `ConcurrentHashMap<String, PricedAt>` (private nested record carrying price + write timestamp); public API exposes `Double` only. Both `FinnhubPriceEvaluator` (US stocks) and `YahooPriceEvaluator` (international stocks) write to it. Consumers (`PullbackBuyTracker`, `DailyPriceProvider`) read from it. Replaced the former `FinnhubPriceEvaluator.lastPriceCache` field. Stale entries (>24 hours) are evicted by `evictStale()`, called from `Scheduler.periodicMaintenance()` as a safety net against persistent values from a downed exchange or failing data source.
-   **`RsiService`:** Core service for RSI calculations. Manages historical price data, calculates RSI values, detects market holidays.
-   **`RsiPriceFetcher`:** Dedicated component for fetching historical price data for RSI calculations.
-   **`InsiderTracker`:** Tracks and reports insider trading activities. Excludes ETFs and international symbols (Finnhub free tier doesn't support non-US listings).
-   **`SectorRotationTracker`:** Tracks industry sector performance from FinViz.
-   **`SectorRotationAnalyzer`:** Statistical analysis component that detects sector rotation signals using Z-Score analysis.
-   **`RelativeStrengthTracker`:** Tracks stock performance relative to SPY benchmark using 50-period EMA crossover detection.
-   **`SectorRelativeStrengthTracker`:** Monitors sector ETF performance vs SPY benchmark. Real-time RS crossover alerts + daily summary. Uses `SymbolRegistry.getAllEtfs()` and `getThematicSymbols()` for report section splitting. Daily summary also appends a "stocks outperforming sector leader" section: identifies the top-ranked ETF (by RS-vs-SPY pctDiff descending) and lists tracked stocks whose RS-vs-leader pctDiff is positive, top 10 sorted descending, with total qualifying count. Uses `RelativeStrengthService.getCurrentRsResult(symbol, leader)` (benchmark-parameterized). Single Telegram message — section is appended, not a separate send.
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
-   **`PullbackBuyTracker`:** Real-time EMA pullback buy alerts. Two entry points so the scheduler can gate domestic vs. international independently: `analyzeDomestic()` runs every 5 min during NYSE hours (inside `stockMarketMonitoring`), `analyzeInternational()` runs every 5 min unconditionally (after `yahooPriceEvaluator::evaluatePrice` so `LivePriceCache` is fresh) and skips per-symbol via `MarketStatusService.isExchangeOpen()`. This closes the prior gap where KRX stocks were never evaluated during their session and XETRA only got the 09:30–11:30 ET overlap. Detects stocks below EMA 9/21 but above EMA 50/100/200, with positive RS and VFI. Reads live prices from `LivePriceCache` (no separate API calls). Per-stock Telegram alerts with 8-hour cooldown via `IgnoreReason.PULLBACK_BUY_ALERT`. Alerts are tagged with a 🏆 _Apex performer_ highlight when the symbol is in `apex_performers` (set populated by the daily sector RS summary).
-   **`AccumulationDetectionTracker`:** Daily (10:00 CET) institutional accumulation detection. Identifies stocks where EMA9 < EMA21 (weak price) but VFI > 0 and VFI > signal (bullish volume). Sends consolidated Telegram alert with streak counter showing consecutive signal days (persisted in `accumulation_streaks` SQLite table). Streak annotation omitted for day-1 signals.
-   **`OhlcvFetcher`:** Fetches daily OHLCV data from Twelve Data for all tracked symbols (ETFs + stocks via `SymbolRegistry.getAll()`). Backfill: 400 data points. Refresh: 5 data points. 8-second delay between requests. Also provides `backfillSymbols(List<String>)` for on-demand backfill of newly added symbols.
-   **`OhlcvBackfillService`:** Processes newly added symbols that need OHLCV data. Reads from `newly_added_symbols` queue table (populated by `/add` command). Filters out symbols no longer tracked before fetching. Batch size: 10. TTL: 24h (expired entries logged and removed atomically via `DELETE ... RETURNING`). Runs in `periodicMaintenance`.
-   **`TelegramClient` / `LocalTelegramGateway` / `TelegramMessageProcessor`:** Telegram integration is profile-aware. All sending components inject `TelegramGateway` interface.
-   **`TelegramCommandDispatcher`:** Routes incoming commands to appropriate processors using the Command pattern.
-   **`TargetPriceProvider`:** Manages the watchlist of symbols to be monitored.
-   **`RootErrorHandler`:** Centralized error handler wrapping all scheduled tasks. `run()` preserves fire-and-log; `runWithStatus()` adds boolean success/failure for dev triggers.
-   **`DevDataSeeder`:** `dev`-only startup seeder that populates SQLite quote history (via `PriceQuoteRepository.saveAll()`), OHLCV data (400 days), RSI/RS/ROC state. Uses `JdbcTemplate` for direct table operations (DELETE, COUNT queries).
-   **`DevJobController`:** `dev`-only manual trigger surface. 14 individual endpoints + 1 composite `run-all` endpoint. Individual endpoints return HTTP 200/500. `run-all` orchestrates 4-phase smoke test execution.

## External API Clients

-   **`FinnhubClient`:** Stock prices and insider transactions. API key required.
-   **`CoinGeckoClient`:** Cryptocurrency prices. No auth.
-   **`FinvizClient`:** Web scraper using JSoup for industry performance. No auth.
-   **`TwelveDataClient`:** Fetches daily OHLCV data. API key required. 8 req/min rate limit. Metered via `ApiRequestMeteringService`.
-   **`YahooFinanceClient`:** Fetches daily OHLCV for international stocks (German/Korean) AND intraday price quotes (via `meta.regularMarketPrice`). No auth. Uses ProcessBuilder + curl to bypass TLS fingerprint blocking. 3s delay between calls. Metered via `ApiRequestMeteringService`. Failures throw `YahooFetchException` (caught silently in OhlcvFetcher and YahooPriceEvaluator — no Telegram alert).

## Data Persistence Components

-   **`SqlitePriceQuoteRepository`:** Historical Finnhub price quotes via `JdbcTemplate`. Used by TailRiskService (for `findDailyChangePercents()`) and as fallback for DailyPriceProvider. Supports batch insert via `saveAll()`.
-   **`SqliteMomentumRocRepository`:** Momentum ROC state via `JdbcTemplate` (previous ROC values for crossover detection).
-   **`SqliteOhlcvRepository`:** Twelve Data daily OHLCV data via `JdbcTemplate` in `twelvedata_daily_ohlcv` table. Primary source for DailyPriceProvider and VfiService. Batch insert via `BatchPreparedStatementSetter`.
-   **`CachingOhlcvRepository`:** `@Primary` decorator over `SqliteOhlcvRepository`. In-memory cache keyed by `(symbol, days)`, populated on cache miss via `ConcurrentHashMap.computeIfAbsent`, stored as immutable copies (`List.copyOf`). Invalidates entries for touched symbols on `saveAll(records)` (extracts distinct symbols from the batch) and `deleteBySymbol(symbol)`. All `OhlcvRepository` consumers receive the decorator transparently — they don't know data came from cache vs. SQLite. Caches empty results too; the same write-path invalidation hook covers the empty-then-populated transition for newly-added symbols.
-   **`SqliteIgnoredSymbolRepository`:** Per-symbol alert suppression via `JdbcTemplate` with reason codes and optional alert thresholds.
-   **`SqliteApiMeteringRepository`:** API request counters per provider via batch `INSERT OR REPLACE`. Flushed periodically by Scheduler's `periodicMaintenance()` (every 10 min) and on shutdown (`@PreDestroy`). `AtomicInteger` map is the in-memory source of truth; SQLite is crash-recovery persistence.
-   **`SqliteAccumulationStreakRepository`:** Accumulation streak data (consecutive signal days per stock) via `INSERT OR REPLACE`. Methods: `save()`, `findBySymbol()`, `deleteAllExcept(Set<String>)`. Streak deleted when signal stops firing.
-   **`SqliteNewlyAddedSymbolRepository`:** Queue table for symbols awaiting OHLCV backfill. `INSERT OR REPLACE` on add, `DELETE ... RETURNING` for atomic expired cleanup. Methods: `insert()`, `findOldest()`, `deleteAll()`, `deleteExpiredReturning()`.
-   **`SqliteApexPerformerRepository`:** Persists the apex performer set — stocks outperforming the top sector ETF (positive RS-vs-leader). Refreshed atomically (DELETE + batch INSERT in a transaction) by `SectorRelativeStrengthTracker.sendDailySectorRsSummary()` on its 16:00 / 21:00 CET cron. Read by `PullbackBuyTracker` to highlight buy alerts. Methods: `replaceAll(Set<String>)`, `findAll()`. Persisted (not in-memory) so the set survives the daily ~23:30 deploy window — required for Korean stock alerts that fire before the next refresh.
-   **`FeatureToggleService`:** Runtime feature flag management with JSON persistence. Loaded once via `@PostConstruct`, updated in-memory and on disk via `setToggle(FeatureToggle, boolean)`. Controlled at runtime via `/toggle` Telegram command. Creates the JSON file if missing; can enable toggles not yet present in the file (enum is the source of truth).
-   **`DatabaseDirectoryInitializer`:** `@PostConstruct` component that ensures the SQLite database parent directory exists at startup. Parses `spring.datasource.url` to extract the file path.
-   **Schema Management:** All DDL centralized in `src/main/resources/schema.sql` (13 tables). Auto-executed on startup via `spring.sql.init.mode=always`.

## Design Patterns

-   **Repository Pattern:** Interface-implementation separation. `PriceQuoteRepository`, `OhlcvRepository`, `MomentumRocRepository`. All implementations use Spring's `JdbcTemplate` (not raw JDBC). DataSource auto-configured via `spring.datasource.*` with HikariCP (pool size 1 for SQLite). Spring's `DataAccessException` propagates naturally (no manual `IllegalStateException` wrapping).
-   **Command Pattern**: Telegram command processing (`/add`, `/remove`, `/rsi`, `/show`, `/set`, `/data reset`, `/toggle`).
-   **Dependency Injection**: Constructor injection via `@RequiredArgsConstructor`. All major components injected.
-   **Profile Gating**: Default = production. `dev` = opt-in local profile.
-   **Scheduler Pattern**: `@Scheduled` with cron expressions and timezone support.
-   **Strategy Pattern**: Different `PriceEvaluator` implementations for different data sources.
-   **Facade Pattern**: `TelegramClient` simplifies Telegram Bot API interaction.
-   **Data Source Fallback**: `DailyPriceProvider` tries OHLCV first, falls back to Finnhub.
-   **Caching Decorator**: Repository-level cache implemented as a `@Primary` decorator over the SQLite implementation. Canonical example: `CachingOhlcvRepository` wraps `SqliteOhlcvRepository`. Consumers inject the interface (`OhlcvRepository`) and receive the decorator transparently — they don't know data came from cache vs. SQLite. The decorator owns invalidation: every write method (`saveAll`, `deleteBySymbol`) invalidates entries for the touched symbols. This pattern fits any read-heavy repository where the underlying data has clear write boundaries.
-   **Per-Reason TTL**: `IgnoreReason` enum carries `ttlSeconds` per value. `TargetPriceProvider.isSymbolIgnored()` uses `reason.getTtlSeconds()` instead of a global constant. Existing alerts use 12h, pullback uses 8h.
-   **Finnhub as Data Ingestion Layer**: `FinnhubPriceEvaluator` fetches live prices for ALL domestic tracked symbols (stocks + ETFs via `symbolRegistry.getAll()`, skipping international) and populates `LivePriceCache`. Loop 1 handles fetch/cache/persist/high-change-alerts for every symbol. Loop 2 evaluates target buy/sell prices from cache (no API calls). `YahooPriceEvaluator` does the same for international symbols. All downstream indicators/trackers read from `LivePriceCache` or SQLite — never make their own API calls.
-   **Phased Smoke Test**: `DevJobController.runAll()` executes 14 jobs in 4 dependency-ordered phases (seed → OHLCV fetch → parallel independents → VFI). Returns aggregate pass/fail with per-job results. `RootErrorHandler.runWithStatus()` provides boolean success/failure without propagating exceptions.
-   **Two-Pass OHLCV Fetch**: `OhlcvFetcher.fetchAndBackfillOhlcv()` runs domestic symbols first (TwelveData, 9s delay, retry + Telegram alert on failure), then international symbols (Yahoo Finance, 3s delay, log-only on failure). International detection via any-dot heuristic (`ticker.contains(".")`).
-   **ProcessBuilder Shell-Out Pattern**: `YahooFinanceClient.executeCurl()` uses Java ProcessBuilder to run curl with specific headers, bypassing Yahoo's TLS fingerprint blocking of Java HTTP clients. Includes `--connect-timeout 5 --max-time 10` curl flags + `waitFor(15s)` Java backstop + `destroyForcibly()` on timeout.

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
| `dailyOhlcvFetch` | Daily 23:00 (Mon-Fri) | CET | Twelve Data OHLCV fetch (400 data points) |
| `weeklyInsiderTradingReport` | Weekly Sat 12:00 | CET | Insider transactions |
| `monthlyApiUsageReport` | Monthly 1st, 00:00 | UTC | API usage statistics |
| `telegramMessagePolling` | Every 60 seconds | UTC | Process Telegram commands |
| `periodicMaintenance` | Every 10 min | — | Cleanup ignored symbols + flush API metering counters + OHLCV backfill for new symbols + cleanup expired backfill entries + evict stale `LivePriceCache` entries |

## Component Relationships

```
Scheduler
├── FinnhubPriceEvaluator → FinnhubClient → Finnhub API
│   ├── LivePriceCache (shared write)
│   └── SqlitePriceQuoteRepository → SQLite DB
├── YahooPriceEvaluator → YahooFinanceClient → Yahoo Finance API
│   ├── LivePriceCache (shared write)
│   ├── MarketStatusService.isExchangeOpen() (XETRA/KRX hours)
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
├── LivePriceCache (shared read by downstream consumers)
├── SectorRelativeStrengthTracker → RelativeStrengthService + SymbolRegistry
├── SectorMomentumRocTracker → MomentumRocService + SymbolRegistry
├── TailRiskTracker → TailRiskService + SymbolRegistry
├── BollingerBandTracker → BollingerBandService + SymbolRegistry
├── EmaTracker → EmaService + SymbolRegistry
├── VfiTracker → VfiService + RelativeStrengthService + SymbolRegistry
│   └── CombinedSignalType: GREEN/YELLOW/RED classification
├── PullbackBuyTracker → EmaService + RelativeStrengthService + VfiService + LivePriceCache + SymbolRegistry + ApexPerformerRepository
│   └── Per-stock alerts with 8h cooldown via IgnoreReason.PULLBACK_BUY_ALERT
├── OhlcvFetcher → TwelveDataClient + YahooFinanceClient + SymbolRegistry
├── StatisticsUtil (shared math: mean, stddev, EMA, ROC, zScore)
├── InsiderTracker → InsiderPersistence
├── SectorRotationTracker → FinvizClient + SectorRotationAnalyzer
├── RootErrorHandler (run + runWithStatus)
├── DevDataSeeder (dev) → seeds SQLite + JSON + OHLCV + LivePriceCache
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
- **Repository Slice Tests**: `@JdbcTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Sql("classpath:schema.sql")`. Spring Boot 4 package: `org.springframework.boot.jdbc.test.autoconfigure`. Uses Spring-managed in-memory SQLite with transactional rollback per test.
- **Plain JUnit + JdbcTemplate**: Tests constructing repositories directly use file-based temp SQLite DBs with manual schema init via `JdbcTemplate.execute()`.
- **Configurable File Paths**: Constructor injection for file paths in tests
- **Lenient stubs**: Used when SymbolRegistry methods return real ETF data via static constants but only specific symbols are relevant to the test
