# Tech Context

This document covers the technologies used, development setup, technical constraints, dependencies, and tool usage patterns.

## Technologies
- **Language:** Java 23
- **Framework:** Spring Boot 3.5.7
- **Build Tool:** Maven 
- **Database:** SQLite (embedded) **NEW**

## Dependencies
- **Spring Boot Starters:** 
  - `spring-boot-starter`: Core Spring Boot functionality
  - `spring-boot-starter-web`: Web application support with embedded Tomcat
  - `spring-boot-starter-validation`: Bean validation with Hibernate validator
  - `spring-boot-starter-test`: Testing support (test scope)
- **Lombok 1.18.34:** Reduces boilerplate code with annotations like `@Slf4j`, `@RequiredArgsConstructor`, `@Getter`, etc.
- **Jackson:** JSON serialization/deserialization with JSR-310 (Java Time) datatype support
- **JSoup 1.22.1:** HTML parsing library for web scraping (FinViz sector data)
- **SQLite JDBC 3.49.0.0:** **NEW** - Embedded SQLite database driver for historical price storage
- **Testing:** 
  - JUnit Jupiter 6.0.1
  - Mockito 5.20.0 (core and junit-jupiter integration)
  - Hamcrest 3.0
  - Spring Boot Test support

## Build Configuration
- **Compiler:** `maven-compiler-plugin` 3.14.1 configured for Java 23 with Lombok annotation processing
- **Packaging:** `spring-boot-maven-plugin` for creating executable JARs
- **Code Coverage:** `jacoco-maven-plugin` 0.8.14 enforces 97% instruction coverage ratio
  - Generates coverage reports during verify phase
  - Fails build if coverage falls below 97%
- **Code Formatting:** `spotless-maven-plugin` 3.0.0 with Google Java Format 1.30.0 (AOSP style)
  - Enforces consistent code style across the project
  - Runs check during build to ensure compliance

## Development Patterns
- **Annotation-Driven Configuration:** Uses Spring annotations like `@Service`, `@Component`, `@Scheduled`, `@Autowired`
- **Constructor Injection:** Preferred DI method using Lombok's `@RequiredArgsConstructor`
- **Repository Pattern:** **NEW** - Clean abstraction for data persistence (`PriceQuoteRepository` interface)
- **Logging:** SLF4J via Lombok's `@Slf4j` annotation
- **Scheduled Tasks:** Spring's `@Scheduled` annotation with cron expressions and timezone support
- **JSON Persistence:** Uses Jackson ObjectMapper to persist data structures to JSON files in the `config/` directory
- **SQLite Persistence:** **NEW** - Uses JDBC with DataSource for SQLite database operations
- **Profile Model:** Default runtime is production-like via `application.yaml`; `dev` is opt-in and used only for local overrides, mocked Telegram output, scheduler disablement, and analytics seeding

## External Data Sources

### API-Based
| Source | Client | Purpose | Auth |
|--------|--------|---------|------|
| Finnhub | `FinnhubClient` | Stock prices, insider transactions | API Key |
| CoinGecko | `CoinGeckoClient` | Cryptocurrency prices | No auth |
| Telegram | `TelegramClient` | Bot messaging | Bot Token |
| Yahoo Finance | `YahooFinanceClient` | Daily OHLCV data (volume + adj close) | No auth |

### Web Scraping
| Source | Client | Purpose | Auth |
|--------|--------|---------|------|
| FinViz | `FinvizClient` | Industry sector performance | No auth |

### Embedded Database (NEW)
| Database | Repository | Purpose | Location |
|----------|------------|---------|----------|
| SQLite | `SqlitePriceQuoteRepository` | Historical Finnhub price quotes | `data/tradebot.db` |
| SQLite | `SqliteMomentumRocRepository` | Momentum ROC state | `data/tradebot.db` |
| SQLite | `SqliteYahooOhlcvRepository` | Yahoo Finance daily OHLCV | `data/tradebot.db` |

## Configuration Files

| File | Format | Purpose |
|------|--------|---------|
| `config/stock-symbols.json` | JSON | Dynamic stock symbol registry |
| `config/target-prices-stocks.json` | JSON | Stock buy/sell targets |
| `config/target-prices-coins.json` | JSON | Crypto buy/sell targets |
| `config/sector-performance.json` | JSON | Sector performance history |
| `config/insider-transactions.json` | JSON | Insider trading data |
| `config/finnhub-monthly-requests.txt` | Text | API metering |
| `config/coingecko-monthly-requests.txt` | Text | API metering |
| `config/dev-telegram-messages.log` | Text | Dev-only local Telegram sink |
| `config/tg-last-processed-message-id.txt` | Text | Telegram message tracking |
| `config/feature-toggles.json` | JSON | **NEW** Runtime feature flags |
| `config/yahoo-monthly-requests.txt` | Text | Yahoo API metering |
| `data/tradebot.db` | SQLite | Historical price data + OHLCV |

## Runtime Profiles

### Default Runtime
- Uses `FINNHUB_API_KEY`
- Uses `COINGECKO_API_KEY`
- Uses `TELEGRAM_BOT_TOKEN`
- Uses `TELEGRAM_BOT_GROUP_CHAT_ID`
- Active when `SPRING_PROFILES_ACTIVE` is unset

### Dev Runtime
- Activated with `SPRING_PROFILES_ACTIVE=dev`
- Uses `FINNHUB_DEV_API_KEY`
- Uses `COINGECKO_DEV_API_KEY`
- Disables schedulers
- Redirects Telegram output to a local sink file
- Uses a separate local SQLite database path and seeded analytics state
- Exposes manual `/dev/jobs/*` endpoints that return HTTP 200/500 based on execution success

## Project Structure

```
src/main/java/org/tradelite/
├── Application.java              # Main entry point
├── Scheduler.java                # Task scheduler
├── RootErrorHandler.java         # Global error handling
├── ThrowingRunnable.java         # Functional interface
├── client/
│   ├── coingecko/               # CoinGecko API client
│   ├── finnhub/                 # Finnhub API client
│   ├── finviz/                  # FinViz web scraper
│   ├── telegram/                # Telegram Bot API
│   └── yahoo/                   # Yahoo Finance OHLCV client
├── common/                      # Shared DTOs and utilities
│   ├── SectorEtfRegistry.java  # Central ETF symbol/name registry (20 ETFs)
├── config/                      # Spring configuration
├── core/                        # Business logic
│   ├── *PriceEvaluator.java    # Price evaluation
│   ├── InsiderTracker.java     # Insider monitoring
│   ├── SectorRotationTracker.java  # Sector tracking
│   ├── SectorRelativeStrengthTracker.java  # Sector RS tracking (uses SectorEtfRegistry)
│   ├── SectorMomentumRocTracker.java  # Sector ROC tracking (uses SectorEtfRegistry)
│   ├── MomentumRocSignal.java  # ROC signal record
│   └── ...
├── quant/                       # Quantitative analysis
│   ├── SkewnessLevel.java      # Skewness classification enum (NEW)
│   ├── TailRiskLevel.java      # Risk level enum
│   ├── TailRiskAnalysis.java   # Analysis result record (kurtosis + skewness)
│   ├── TailRiskService.java    # Kurtosis + skewness calculation
│   ├── TailRiskTracker.java    # Sector tail risk monitoring (uses SectorEtfRegistry)
│   ├── EmaSignalType.java      # GREEN/YELLOW/RED classification enum
│   ├── EmaAnalysis.java        # Analysis result record (symbol, price, EMAs, signal)
│   ├── EmaService.java         # 9/21/50/100/200-day EMA calculation + classification
│   └── EmaTracker.java         # Daily EMA report orchestrator
├── repository/                  # Data persistence layer
│   ├── PriceQuoteEntity.java   # Entity class
│   ├── PriceQuoteRepository.java # Interface (includes findDailyChangePercents)
│   ├── SqlitePriceQuoteRepository.java # SQLite price implementation
│   ├── MomentumRocRepository.java # Interface
│   ├── SqliteMomentumRocRepository.java # SQLite ROC implementation
│   ├── YahooOhlcvRepository.java # Interface for Yahoo OHLCV
│   └── SqliteYahooOhlcvRepository.java # SQLite Yahoo OHLCV implementation
├── service/                     # Application services
├── utils/                       # Utility classes
└── web/                         # Web endpoints (if any)
```

## Testing Strategy

### Test Coverage
- **Target:** 97% instruction coverage
- **Current:** 97%
- **Total Tests:** 852

### Testing Libraries
```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>6.0.1</version>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.20.0</version>
</dependency>
```

### Test Patterns
- **Unit Tests:** All components have dedicated test classes
- **Mocking:** External dependencies mocked with Mockito
- **Argument Captors:** For verifying complex method arguments
- **Temp Files:** `@TempDir` for file persistence tests
- **Configurable File Paths:** Constructor injection for file paths enables temp directory usage in tests
- **HTML Mocking:** JSoup document creation for web scraping tests
- **In-Memory SQLite:** Unique temp DB files per test to avoid conflicts

## Web Scraping with JSoup

### Usage Pattern
```java
Document doc = Jsoup.connect(url)
    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)...")
    .timeout(10000)
    .get();

Elements rows = doc.select("table.table-light tr");
for (Element row : rows) {
    Elements cells = row.select("td");
    String name = cells.get(0).text();
    // Parse data...
}
```

## SQLite Database Pattern (NEW)

### DataSource Configuration
```java
@Bean
public DataSource dataSource(@Value("${tradebot.database.path}") String dbPath) {
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + dbPath);
    return dataSource;
}
```

### Repository Usage Pattern
```java
// Auto-initializing schema on construction
public SqlitePriceQuoteRepository(DataSource dataSource) {
    this.dataSource = dataSource;
    initializeSchema();  // Creates table and indexes if not exist
}

// JDBC with prepared statements
try (Connection conn = dataSource.getConnection();
     PreparedStatement pstmt = conn.prepareStatement(sql)) {
    pstmt.setString(1, symbol);
    pstmt.setLong(2, timestamp);
    // ...
    pstmt.executeUpdate();
}
```

### UTC Timestamp Best Practice
```java
// Storage: Always use UTC epoch seconds
long timestamp = Instant.now().getEpochSecond();

// Query: Use UTC for date conversions
long startOfDay = date.atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
```

### Test Database Isolation
```java
@BeforeEach
void setUp() {
    // Unique temp file per test to avoid conflicts
    testDbPath = "target/test-db-" + UUID.randomUUID() + ".db";
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + testDbPath);
    repository = new SqlitePriceQuoteRepository(dataSource);
}

@AfterEach
void tearDown() {
    new File(testDbPath).delete();
}
```

## Build Commands

```bash
# Run all tests
mvn test

# Run with coverage
mvn verify

# Format code
mvn spotless:apply

# Check formatting
mvn spotless:check

# Package application
mvn package
```

## Application Configuration (application.yaml)

```yaml
tradebot:
  database:
    path: data/tradebot.db  # SQLite database location
  telegram:
    bot-token: ${TELEGRAM_BOT_TOKEN}
    chat-id: ${TELEGRAM_CHAT_ID}
  finnhub:
    api-key: ${FINNHUB_API_KEY}
