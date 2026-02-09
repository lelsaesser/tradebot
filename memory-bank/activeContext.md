# Active Context

## Current Work Focus
✅ **COMPLETED**: Sector Rotation Tracking Feature (February 9, 2026)
- Web scraper for FinViz industry performance data
- Daily scheduled task to fetch and store data
- Telegram notifications for top/bottom performers
- All 330 tests passing, build successful

## Recent Changes (February 2026)

### Sector Rotation Tracking - COMPLETED

1. **FinViz Web Scraper** ✅
   - Created `FinvizClient` using JSoup for HTML parsing
   - Scrapes industry performance data from https://finviz.com/groups.ashx?g=industry&v=140
   - Parses performance metrics: daily change, weekly, monthly, quarterly, half-year, yearly, YTD
   - No browser automation needed (pure HTTP + HTML parsing)

2. **Data Persistence** ✅
   - `SectorPerformancePersistence` stores snapshots in `config/sector-performance.json`
   - Historical data maintained for trend analysis
   - Methods for top/bottom performers by period (daily, weekly, monthly, quarterly, yearly)

3. **Automated Tracking** ✅
   - `SectorRotationTracker` orchestrates fetch → store → report workflow
   - Sends daily Telegram report with top 5 gainers and losers
   - Scheduled daily at 10:30 PM ET (after US market close) on weekdays

4. **New Files Created** ✅
   - `src/main/java/org/tradelite/client/finviz/FinvizClient.java`
   - `src/main/java/org/tradelite/client/finviz/dto/IndustryPerformance.java`
   - `src/main/java/org/tradelite/core/SectorPerformanceSnapshot.java`
   - `src/main/java/org/tradelite/core/SectorPerformancePersistence.java`
   - `src/main/java/org/tradelite/core/SectorRotationTracker.java`
   - `src/test/java/org/tradelite/client/finviz/FinvizClientTest.java`
   - `src/test/java/org/tradelite/core/SectorPerformancePersistenceTest.java`
   - `src/test/java/org/tradelite/core/SectorRotationTrackerTest.java`

5. **Modified Files** ✅
   - `pom.xml` - Added JSoup 1.18.3 dependency
   - `Scheduler.java` - Added dailySectorRotationTracking() method
   - `SchedulerTest.java` - Updated with SectorRotationTracker mock
   - `BeanConfig.java` - Registered new beans

### Previous: Dynamic Stock Symbol Management - COMPLETED

1. **StockSymbol Refactoring**: Converted from enum to regular class ✅
2. **New Telegram Commands**: `/add TICKER Display_Name` and `/remove TICKER` ✅
3. **StockSymbolRegistry**: Dynamic symbol management with JSON persistence ✅

## Testing Summary

- **Total Tests**: 330 ✅
- **Build Status**: SUCCESS ✅
- New sector rotation tests: 22 tests across 3 test files

## Active Decisions and Considerations

### Sector Rotation Architecture
- **JSoup over Playwright**: Simpler, faster, no browser dependencies
- **JSON Persistence**: Consistent with other config files pattern
- **No Telegram Command**: Scheduler-only, no manual trigger needed
- **Historical Storage**: Full snapshots stored for future trend analysis

### Data Model
```java
record IndustryPerformance(
    String name,
    BigDecimal perfWeek,
    BigDecimal perfMonth, 
    BigDecimal perfQuarter,
    BigDecimal perfHalf,
    BigDecimal perfYear,
    BigDecimal perfYtd,
    BigDecimal change  // daily change
)
```

### Telegram Report Format
```
📊 *Daily Sector Performance Report*
📅 2026-02-09

📈 *Top 5 Daily Gainers:*
1. Technology: +5.25%
2. Healthcare: +3.50%
...

📉 *Bottom 5 Daily Losers:*
1. Energy: -4.20%
2. Financials: -2.80%
...

📈 *Top 5 Weekly Gainers:*
...
```

## Important Patterns and Preferences

### Web Scraping Pattern (FinViz)
```java
Document doc = Jsoup.connect(url)
    .userAgent("Mozilla/5.0...")
    .timeout(10000)
    .get();
Elements rows = doc.select("table.table-light tr");
```

### Persistence Pattern (Sector Data)
```java
// Save snapshot
persistence.saveSnapshot(new SectorPerformanceSnapshot(
    LocalDate.now(), performances));

// Get top performers
List<IndustryPerformance> top = persistence.getTopPerformersByWeek(5);
```

### Scheduled Task Pattern
```java
@Scheduled(cron = "0 30 22 * * MON-FRI", zone = "America/New_York")
public void dailySectorRotationTracking() {
    rootErrorHandler.run(sectorRotationTracker::fetchAndStoreDailyPerformance);
}
```

## Configuration Files
- `config/stock-symbols.json` - Stock symbol registry (38 symbols)
- `config/target-prices-stocks.json` - Stock target prices
- `config/target-prices-coins.json` - Crypto target prices
- `config/sector-performance.json` - Sector performance history (NEW)
- `config/insider-transactions.json` - Insider trading data

## Next Iteration Opportunities

### Sector Rotation Enhancements (Future)
- Trend analysis over multiple weeks
- Sector rotation alerts (big changes)
- Historical comparison reports
- Sector heatmap visualization
- Correlation with market indices

### General Enhancements
- Bulk import/export of stock symbols
- Rate limiting for API calls
- Performance optimizations