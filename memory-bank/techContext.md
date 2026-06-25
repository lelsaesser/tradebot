# Tech Context

This document covers the technologies used, development setup, technical constraints, dependencies, and tool usage patterns.

## Technologies
- **Language:** Java 25
- **Framework:** Spring Boot 4.0.6
- **Build Tool:** Maven (use `mvn` directly — there is no `./mvnw` wrapper)
- **Database:** SQLite (embedded, via JdbcTemplate)

### Dashboard (React Frontend)
- **Framework:** React 19 + Vite 6
- **Language:** TypeScript
- **Styling:** Tailwind CSS v4
- **Testing:** Vitest + @testing-library/react
- **Dev proxy:** Vite server proxy → localhost:9090
- **SSE:** Single EventSource in SSEProvider context, `/api/v1/events`

## Dependencies
- **Spring Boot Starters:** 
  - `spring-boot-starter`: Core Spring Boot functionality
  - `spring-boot-starter-web`: Web application support with embedded Tomcat
  - `spring-boot-starter-validation`: Bean validation with Hibernate validator
  - `spring-boot-starter-jdbc`: JdbcTemplate + HikariCP connection pool + schema.sql auto-init
  - `spring-boot-starter-test`: Testing support (test scope)
  - `spring-boot-starter-jdbc-test`: @JdbcTest slice test support (test scope)
- **Lombok 1.18.34:** Reduces boilerplate code with annotations like `@Slf4j`, `@RequiredArgsConstructor`, `@Getter`, etc.
- **Jackson:** JSON serialization/deserialization with JSR-310 (Java Time) datatype support
- **JSoup 1.22.2:** HTML parsing library for web scraping (FinViz sector data)
- **SQLite JDBC 3.53.1.0:** Embedded SQLite database driver for historical price storage
- **Apache Commons Statistics Descriptive 1.3+:** Bias-corrected sample moment estimators (Fisher-Pearson G1 skewness, G2 kurtosis). Consumed by `TailRiskService` for fat-tail detection. Industry-standard formulas matching Pandas / NumPy `bias=False` / Excel `KURT`/`SKEW` / Basel-FRTB. Replaces the legacy `commons-math3` artifact (which Apache themselves marked as "not supported anymore" — see `apache/commons-math` README). Single direct dep with two tiny transitive ones (`commons-numbers-core`, `commons-numbers-arrays`).
- **Testing:** 
  - JUnit Jupiter 6.1.0
  - Mockito 5.23.0 (core and junit-jupiter integration)
  - Hamcrest 3.0
  - Spring Boot Test support

## Build Configuration
- **Compiler:** `maven-compiler-plugin` 3.15.0 configured for Java 25 with Lombok annotation processing
- **Packaging:** `spring-boot-maven-plugin` for creating executable JARs
- **Code Coverage:** `jacoco-maven-plugin` 0.8.14 enforces 97% instruction coverage ratio
- **Code Formatting:** `spotless-maven-plugin` 3.6.0 with Google Java Format 1.30.0 (AOSP style)

## External Data Sources

### API-Based
| Source | Client | Purpose | Auth |
|--------|--------|---------|------|
| Finnhub | `FinnhubClient` | Stock prices, insider transactions | API Key |
| CoinGecko | `CoinGeckoClient` | Cryptocurrency prices | No auth |
| Telegram | `TelegramClient` | Bot messaging | Bot Token |
| Twelve Data | `TwelveDataClient` | Daily OHLCV data (400 data points) | API Key |
| Yahoo Finance | `YahooFinanceClient` | International stock OHLCV + intraday price quotes | No auth (`java.net.http.HttpClient`) |
| Enrico (Kayaposoft) | `EnricoClient` | Public-holiday calendars per country | No auth |
| FRED | `FredClient` | US Treasury macro time series (yield-curve spreads, real yield, term premium). Free API key (email signup); single 32-char key as `api_key` query param. Not metered — ~4 requests/day total. ToS requires attribution in consumer-facing renderings. Added in #516. | API Key |

### Web Scraping
| Source | Client | Purpose | Auth |
|--------|--------|---------|------|
| FinViz | `FinvizClient` | Industry sector performance | No auth |

### Embedded Database
| Table | Repository | Purpose |
|-------|------------|---------|
| `finnhub_price_quotes` | `SqlitePriceQuoteRepository` | Historical Finnhub price quotes |
| `momentum_roc_state` | `SqliteMomentumRocRepository` | Momentum ROC state |
| `twelvedata_daily_ohlcv` | `SqliteOhlcvRepository` | Twelve Data daily OHLCV (400 data points) |
| `ignored_symbols` | `SqliteIgnoredSymbolRepository` | Per-symbol alert suppression with reason and TTL |
| `rs_crossover_state` | `SqliteRsCrossoverStateRepository` | Relative strength crossover detection state |
| `sector_rs_streaks` | `SqliteSectorRsStreakRepository` | Consecutive days of outperformance/underperformance |
| `insider_transactions` | `SqliteInsiderTransactionRepository` | Weekly insider transaction counts |
| `industry_performance` | `SqliteSectorPerformanceRepository` | FinViz sector/industry performance snapshots |
| `target_prices` | `SqliteTargetPriceRepository` | Buy/sell target prices (stocks + coins, merged with asset_type) |
| `stock_symbols` | `SqliteStockSymbolRepository` | All tracked stock symbols |
| `api_request_metering` | `SqliteApiMeteringRepository` | Monthly API request counters per provider (periodic flush) |
| `accumulation_streaks` | `SqliteAccumulationStreakRepository` | Consecutive days of accumulation signal per stock |

All repositories use Spring's `JdbcTemplate` (not raw JDBC). Schema is centralized in `src/main/resources/schema.sql` and auto-initialized via `spring.sql.init.mode=always`. DataSource is auto-configured via `application.yaml` (`spring.datasource.*`) with HikariCP connection pool (max pool size 1 for SQLite single-writer). `DatabaseDirectoryInitializer` ensures the DB parent directory exists at startup.

## Configuration Files

| File | Format | Purpose |
|------|--------|---------|
| `config/stock-symbols.json` | JSON | Dynamic stock symbol registry (migrated to SQLite #326, pending removal #359) |
| `config/target-prices-stocks.json` | JSON | Stock buy/sell targets (migrated to SQLite #326, pending removal #359) |
| `config/target-prices-coins.json` | JSON | Crypto buy/sell targets (migrated to SQLite #326, pending removal #359) |
| `config/insider-transactions.json` | JSON | Insider trading data |
| `config/feature-toggles.json` | JSON | Runtime feature flags (FINNHUB_PRICE_COLLECTION, EMA_REPORT, VFI_REPORT, PULLBACK_BUY_ALERT, ACCUMULATION_DETECTION, EARNINGS_CALENDAR_ALERT) |
| `config/dev-telegram-messages.log` | Text | Dev-only local Telegram sink |
| `data/tradebot.db` | SQLite | All SQLite tables |
| `src/main/resources/logback-spring.xml` | XML | Logback configuration: Spring Boot defaults via `<include>` + global `SecretRedactingTurboFilter` registration |

## Runtime Profiles

### Default Runtime (Production)
- Uses `FINNHUB_API_KEY`, `COINGECKO_API_KEY`, `TWELVEDATA_API_KEY`, `FRED_API_KEY`
- Uses `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_GROUP_CHAT_ID`
- All schedulers active

### Dev Runtime
- Activated with `SPRING_PROFILES_ACTIVE=dev`
- Uses `*_DEV_API_KEY` variants
- Disables schedulers, redirects Telegram to local sink file
- Exposes `/dev/jobs/*` manual trigger endpoints (see Dev Endpoints below)
- DevDataSeeder populates synthetic data (400 days OHLCV, RSI, RS, ROC)
- Bruno API collection for manual testing (see Bruno Collection below)
- Smoke test script for pre-deployment validation (see Smoke Test below)

### Dev Endpoints (`DevJobController`)

All endpoints are POST, dev-profile-only, and return `{"status":"ok","job":"<name>"}` on success or `{"status":"error","job":"<name>","message":"check logs"}` on failure (HTTP 500).

| Endpoint | Purpose |
|----------|---------|
| `/dev/jobs/stock-monitoring` | Manual stock market monitoring |
| `/dev/jobs/hourly-signals` | Hourly signal monitoring (BB + RSI) |
| `/dev/jobs/crypto-monitoring` | Cryptocurrency market monitoring |
| `/dev/jobs/rs-monitoring` | Relative strength monitoring |
| `/dev/jobs/insider-report` | Weekly insider trading report |
| `/dev/jobs/sector-rotation` | Daily sector rotation tracking |
| `/dev/jobs/sector-rs-summary` | Daily sector RS report |
| `/dev/jobs/tail-risk` | Daily tail risk monitoring |
| `/dev/jobs/ema-report` | EMA classification report |
| `/dev/jobs/monthly-api-usage` | Monthly API usage report |
| `/dev/jobs/seed-analytics` | Reseed all dev data from scratch |
| `/dev/jobs/ohlcv-fetch` | OHLCV data fetch from Twelve Data |
| `/dev/jobs/vfi-report` | VFI + RS combined report |
| `/dev/jobs/pullback-buy-alert` | EMA pullback buy alert scan |
| `/dev/jobs/yahoo-price-evaluation` | Yahoo intraday price evaluation (international) |
| `/dev/jobs/earnings-calendar` | Earnings calendar 7-day look-ahead |
| `/dev/jobs/accumulation-detection` | Institutional accumulation detection |
| `/dev/jobs/market-holiday-notification` | Consolidated "Markets closed today" alert across NYSE + international exchanges |
| `/dev/jobs/treasury` | US Treasury macro report (yield curve + real yield + term premium via FRED) |
| `/dev/jobs/run-all` | Phased smoke test (runs all 19 jobs) |

### Bruno API Collection

Location: `TradeliteBrunoCollection/DevController/`

Bruno (open-source API client) collection for manually triggering dev endpoints. All requests target `http://localhost:9090`. The collection contains one request file per endpoint above plus `runAll.yml` for the phased smoke test. The collection can be opened in Bruno by pointing it at the `TradeliteBrunoCollection/` directory.

### Pre-Deployment Smoke Test

**Script:** `scripts/run-smoke-test.sh`

Bash script that validates all system components work together before deployment:
- Takes optional base URL argument (defaults to `http://localhost:9090`)
- POSTs to `/dev/jobs/run-all` with 300-second timeout
- Parses JSON response and checks `status` field
- Exits 0 on `"ok"`, exits 1 on any other status or curl failure

**`/dev/jobs/run-all` Execution Phases:**

| Phase | Jobs | Purpose |
|-------|------|---------|
| 1 | `seed-analytics` | Reseed dev database (clean slate) |
| 2 | `ohlcv-fetch` (3 symbols only) | Minimal OHLCV data for VFI dependency |
| 3 | 10 jobs in parallel: stock-monitoring, hourly-signals, crypto-monitoring, rs-monitoring, insider-report, sector-rotation, sector-rs-summary, tail-risk, ema-report, monthly-api-usage, pullback-buy-alert | All independent jobs |
| 4 | `vfi-report` | Depends on OHLCV data from Phase 2 |

**Response format:**
```json
{
  "status": "ok|partial|error",
  "total": 13,
  "passed": 13,
  "failed": 0,
  "results": { "seed-analytics": "ok", "ohlcv-fetch": "ok", ... }
}
```
- HTTP 200 if all pass (`status: "ok"`)
- HTTP 207 (Multi-Status) if any fail (`status: "partial"` or `"error"`)

## Project Structure

```
src/main/java/org/tradelite/
├── Application.java              # Main entry point
├── Scheduler.java                # Task scheduler
├── RootErrorHandler.java         # Global error handling
├── DevDataSeeder.java            # Dev-only data seeder (400 days OHLCV)
├── DevJobController.java         # Dev-only manual job endpoints
├── client/
│   ├── coingecko/               # CoinGecko API client
│   ├── finnhub/                 # Finnhub API client
│   ├── finviz/                  # FinViz web scraper
│   ├── telegram/                # Telegram Bot API + command processors
│   └── twelvedata/              # Twelve Data OHLCV client
├── common/                      # Shared DTOs and utilities
│   ├── SymbolRegistry.java      # Unified symbol registry (ETFs + stocks)
│   ├── StockSymbol.java         # Symbol DTO (ticker + companyName)
│   ├── OhlcvRecord.java         # OHLCV data record
│   ├── FeatureToggle.java       # Feature toggle enum
│   └── ...
├── config/                      # Spring configuration
│   ├── BeanConfig.java          # RestTemplate + ObjectMapper beans
│   ├── DatabaseDirectoryInitializer.java  # @PostConstruct DB dir creation
│   ├── TradebotApiProperties.java
│   └── TradebotTelegramProperties.java
├── core/                        # Business logic
│   ├── *PriceEvaluator.java     # Price evaluation
│   ├── InsiderTracker.java      # Insider monitoring
│   ├── SectorRotationTracker.java
│   ├── SectorRelativeStrengthTracker.java
│   ├── SectorMomentumRocTracker.java
│   └── ...
├── logging/                     # Logging infrastructure
│   └── SecretRedactingTurboFilter.java  # Global Logback filter dropping events containing known secret shapes (#470)
├── quant/                       # Quantitative analysis
│   ├── VfiService.java          # Volume Flow Indicator calculation
│   ├── VfiTracker.java          # Combined RS+VFI daily report
│   ├── CombinedSignalType.java  # GREEN/YELLOW/RED enum
│   ├── BollingerBandService.java / BollingerBandTracker.java
│   ├── EmaService.java / EmaTracker.java
│   ├── TailRiskService.java / TailRiskTracker.java
│   ├── TailRiskWindow.java      # (lookbackCalendarDays, minDataPoints) record
│   ├── StatisticsUtil.java      # Shared math utilities
│   └── ...
├── repository/                  # Data persistence layer
│   ├── PriceQuoteRepository.java / SqlitePriceQuoteRepository.java
│   ├── OhlcvRepository.java / SqliteOhlcvRepository.java
│   ├── MomentumRocRepository.java / SqliteMomentumRocRepository.java
│   └── ...
├── service/                     # Application services
│   ├── DailyPriceProvider.java  # OHLCV-first, Finnhub-fallback
│   ├── OhlcvFetcher.java        # Twelve Data OHLCV orchestration
│   ├── RelativeStrengthService.java
│   ├── MomentumRocService.java
│   ├── RsiService.java
│   ├── FeatureToggleService.java
│   └── ...
└── utils/                       # Utility classes
```

## Testing Strategy

### Test Coverage
- **Target:** 97% instruction coverage
- **Current:** 97%
- **Total Tests:** ~1164

### Test Patterns
- **Unit Tests:** All components have dedicated test classes
- **Mocking:** External dependencies mocked with Mockito
- **Argument Captors:** For verifying complex method arguments
- **Temp Files:** `@TempDir` for file persistence tests
- **Repository Slice Tests:** `@JdbcTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Sql("classpath:schema.sql")` for repository tests with Spring-managed in-memory SQLite. Note: Spring Boot 4 relocated these annotations to `org.springframework.boot.jdbc.test.autoconfigure`.
- **Plain JUnit + JdbcTemplate:** Tests that construct repositories manually (e.g., `TargetPriceProviderTest`) use file-based temp SQLite DBs with `JdbcTemplate` and manual schema init.
- **Lenient stubs:** For SymbolRegistry mocks (real ETF constants + specific symbol stubs)
- **Configurable delays:** `OhlcvFetcher.setRequestDelayMs(0)` in tests to avoid 8s sleeps

## Build Commands

**Use `mvn` directly. There is no Maven wrapper (`./mvnw`) and no Gradle in this project.** The `mvn` binary is on `PATH`; do not look for or invoke `./mvnw`.

```bash
mvn test              # Run all tests
mvn verify            # Run with coverage check
mvn spotless:apply    # Format code
mvn spotless:check    # Check formatting
mvn package           # Package application
```
