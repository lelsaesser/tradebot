# Tech Context

This document covers the technologies used, development setup, technical constraints, dependencies, and tool usage patterns.

## Technologies
- **Language:** Java 23
- **Framework:** Spring Boot 3.5.7
- **Build Tool:** Maven 

## Dependencies
- **Spring Boot Starters:** 
  - `spring-boot-starter`: Core Spring Boot functionality
  - `spring-boot-starter-web`: Web application support with embedded Tomcat
  - `spring-boot-starter-validation`: Bean validation with Hibernate validator
  - `spring-boot-starter-test`: Testing support (test scope)
- **Lombok 1.18.34:** Reduces boilerplate code with annotations like `@Slf4j`, `@RequiredArgsConstructor`, `@Getter`, etc.
- **Jackson:** JSON serialization/deserialization with JSR-310 (Java Time) datatype support
- **JSoup 1.22.1:** **NEW** HTML parsing library for web scraping (FinViz sector data)
- **Testing:** 
  - JUnit Jupiter 6.0.1
  - Mockito 5.20.0 (core and junit-jupiter integration)
  - Hamcrest 3.0
  - Spring Boot Test support

## Build Configuration
- **Compiler:** `maven-compiler-plugin` 3.14.1 configured for Java 23 with Lombok annotation processing
- **Packaging:** `spring-boot-maven-plugin` for creating executable JARs
- **Code Coverage:** `jacoco-maven-plugin` 0.8.14 enforces 99% instruction coverage ratio (increased from 98%)
  - Generates coverage reports during verify phase
  - Fails build if coverage falls below 99%
- **Code Formatting:** `spotless-maven-plugin` 3.0.0 with Google Java Format 1.30.0 (AOSP style)
  - Enforces consistent code style across the project
  - Runs check during build to ensure compliance

## Development Patterns
- **Annotation-Driven Configuration:** Uses Spring annotations like `@Service`, `@Component`, `@Scheduled`, `@Autowired`
- **Constructor Injection:** Preferred DI method using Lombok's `@RequiredArgsConstructor`
- **Logging:** SLF4J via Lombok's `@Slf4j` annotation
- **Scheduled Tasks:** Spring's `@Scheduled` annotation with cron expressions and timezone support
- **JSON Persistence:** Uses Jackson ObjectMapper to persist data structures to JSON files in the `config/` directory

## External Data Sources

### API-Based
| Source | Client | Purpose | Auth |
|--------|--------|---------|------|
| Finnhub | `FinnhubClient` | Stock prices, insider transactions | API Key |
| CoinGecko | `CoinGeckoClient` | Cryptocurrency prices | No auth |
| Telegram | `TelegramClient` | Bot messaging | Bot Token |

### Web Scraping (NEW)
| Source | Client | Purpose | Auth |
|--------|--------|---------|------|
| FinViz | `FinvizClient` | Industry sector performance | No auth |

## Configuration Files

| File | Format | Purpose |
|------|--------|---------|
| `config/stock-symbols.json` | JSON | Dynamic stock symbol registry |
| `config/target-prices-stocks.json` | JSON | Stock buy/sell targets |
| `config/target-prices-coins.json` | JSON | Crypto buy/sell targets |
| `config/sector-performance.json` | JSON | **NEW** Sector performance history |
| `config/insider-transactions.json` | JSON | Insider trading data |
| `config/finnhub-monthly-requests.txt` | Text | API metering |
| `config/coingecko-monthly-requests.txt` | Text | API metering |
| `config/tg-last-processed-message-id.txt` | Text | Telegram message tracking |

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
│   ├── finviz/                  # NEW: FinViz web scraper
│   └── telegram/                # Telegram Bot API
├── common/                      # Shared DTOs and utilities
├── config/                      # Spring configuration
├── core/                        # Business logic
│   ├── *PriceEvaluator.java    # Price evaluation
│   ├── InsiderTracker.java     # Insider monitoring
│   ├── SectorRotationTracker.java  # NEW: Sector tracking
│   └── ...
├── service/                     # Application services
├── utils/                       # Utility classes
└── web/                         # Web endpoints (if any)
```

## Testing Strategy

### Test Coverage
- **Target:** 99% instruction coverage
- **Current:** 97% (acceptable)
- **Total Tests:** 330

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
- **HTML Mocking:** JSoup document creation for web scraping tests

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

### Advantages over Browser Automation
- No Playwright/Selenium dependencies
- No browser binaries required
- Faster execution
- Lower memory footprint
- Simpler deployment

### Test Mocking
```java
Document mockDoc = Jsoup.parse("<html>...</html>");
when(finvizClient.fetchDocument()).thenReturn(mockDoc);
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