# Tech Context

This document covers the technologies used, development setup, technical constraints, dependencies, and tool usage patterns.

## Technologies
- **Language:** Java 23
- **Framework:** Spring Boot 3.5.7
- **Build Tool:** Maven 
- **Database:** SQLite (embedded)

## Dependencies
- **Spring Boot Starters:** 
  - `spring-boot-starter`: Core Spring Boot functionality
  - `spring-boot-starter-web`: Web application support with embedded Tomcat
  - `spring-boot-starter-validation`: Bean validation with Hibernate validator
  - `spring-boot-starter-test`: Testing support (test scope)
- **Lombok 1.18.34:** Reduces boilerplate code with annotations like `@Slf4j`, `@RequiredArgsConstructor`, `@Getter`, etc.
- **Jackson:** JSON serialization/deserialization with JSR-310 (Java Time) datatype support
- **JSoup 1.22.1:** HTML parsing library for web scraping (FinViz sector data)
- **SQLite JDBC 3.49.0.0:** Embedded SQLite database driver for historical price storage
- **Testing:** 
  - JUnit Jupiter 6.0.1
  - Mockito 5.20.0 (core and junit-jupiter integration)
  - Hamcrest 3.0
  - Spring Boot Test support

## Build Configuration
- **Compiler:** `maven-compiler-plugin` 3.14.1 configured for Java 23 with Lombok annotation processing
- **Packaging:** `spring-boot-maven-plugin` for creating executable JARs
- **Code Coverage:** `jacoco-maven-plugin` 0.8.14 enforces 97% instruction coverage ratio
- **Code Formatting:** `spotless-maven-plugin` 3.0.0 with Google Java Format 1.30.0 (AOSP style)

## External Data Sources

### API-Based
| Source | Client | Purpose | Auth |
|--------|--------|---------|------|
| Finnhub | `FinnhubClient` | Stock prices, insider transactions | API Key |
| CoinGecko | `CoinGeckoClient` | Cryptocurrency prices | No auth |
| Telegram | `TelegramClient` | Bot messaging | Bot Token |
| Twelve Data | `TwelveDataClient` | Daily OHLCV data (400 data points) | API Key |

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

## Configuration Files

| File | Format | Purpose |
|------|--------|---------|
| `config/stock-symbols.json` | JSON | Dynamic stock symbol registry |
| `config/target-prices-stocks.json` | JSON | Stock buy/sell targets |
| `config/target-prices-coins.json` | JSON | Crypto buy/sell targets |
| `config/sector-performance.json` | JSON | Sector performance history |
| `config/insider-transactions.json` | JSON | Insider trading data |
| `config/feature-toggles.json` | JSON | Runtime feature flags (FINNHUB_PRICE_COLLECTION, EMA_REPORT, VFI_REPORT) |
| `config/finnhub-monthly-requests.txt` | Text | Finnhub API metering |
| `config/coingecko-monthly-requests.txt` | Text | CoinGecko API metering |
| `config/twelvedata-monthly-requests.txt` | Text | Twelve Data API metering |
| `config/dev-telegram-messages.log` | Text | Dev-only local Telegram sink |
| `data/tradebot.db` | SQLite | All SQLite tables |

## Runtime Profiles

### Default Runtime (Production)
- Uses `FINNHUB_API_KEY`, `COINGECKO_API_KEY`, `TWELVEDATA_API_KEY`
- Uses `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_GROUP_CHAT_ID`
- All schedulers active

### Dev Runtime
- Activated with `SPRING_PROFILES_ACTIVE=dev`
- Uses `*_DEV_API_KEY` variants
- Disables schedulers, redirects Telegram to local sink file
- Exposes `/dev/jobs/*` manual trigger endpoints (vfi-report, ohlcv-fetch, hourly-signals, etc.)
- DevDataSeeder populates synthetic data (400 days OHLCV, RSI, RS, ROC)
- Bruno API collection at `TradeliteBrunoCollection/DevController/`

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
├── core/                        # Business logic
│   ├── *PriceEvaluator.java     # Price evaluation
│   ├── InsiderTracker.java      # Insider monitoring
│   ├── SectorRotationTracker.java
│   ├── SectorRelativeStrengthTracker.java
│   ├── SectorMomentumRocTracker.java
│   └── ...
├── quant/                       # Quantitative analysis
│   ├── VfiService.java          # Volume Flow Indicator calculation
│   ├── VfiTracker.java          # Combined RS+VFI daily report
│   ├── CombinedSignalType.java  # GREEN/YELLOW/RED enum
│   ├── BollingerBandService.java / BollingerBandTracker.java
│   ├── EmaService.java / EmaTracker.java
│   ├── TailRiskService.java / TailRiskTracker.java
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
- **Total Tests:** 923

### Test Patterns
- **Unit Tests:** All components have dedicated test classes
- **Mocking:** External dependencies mocked with Mockito
- **Argument Captors:** For verifying complex method arguments
- **Temp Files:** `@TempDir` for file persistence tests
- **In-Memory SQLite:** Unique temp DB files per test
- **Lenient stubs:** For SymbolRegistry mocks (real ETF constants + specific symbol stubs)
- **Configurable delays:** `OhlcvFetcher.setRequestDelayMs(0)` in tests to avoid 8s sleeps

## Build Commands

```bash
mvn test              # Run all tests
mvn verify            # Run with coverage check
mvn spotless:apply    # Format code
mvn spotless:check    # Check formatting
mvn package           # Package application
```
