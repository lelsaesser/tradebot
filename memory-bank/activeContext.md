# Active Context

## Current Work Focus
✅ **COMPLETED**: Feature Toggle System (February 18, 2026)
- JSON-based runtime feature toggles without restart
- 3-minute cache TTL for dynamic updates
- `FeatureToggleService` with `isEnabled(String)` API
- 17 comprehensive unit tests, all passing

## Recent Changes (February 2026)

### Feature Toggle System - COMPLETED (Feb 18, 2026)

1. **New Service Component** ✅
   - `FeatureToggleService` - Runtime feature flag management
   - 3-minute (180 seconds) cache TTL for file re-reads
   - Thread-safe synchronized cache refresh
   - Graceful error handling (logs errors, returns false on failure)

2. **Configuration File** ✅
   - `config/feature-toggles.json` - Simple JSON key-value pairs
   - Edit manually to enable/disable features at runtime
   - No restart required - changes detected within 3 minutes

3. **Usage Example** ✅
   ```java
   @Autowired
   private FeatureToggleService featureToggleService;

   public void someMethod() {
       if (featureToggleService.isEnabled("demotrading")) {
           // feature-specific code
       }
   }
   ```

4. **Files Created** ✅
   - `src/main/java/org/tradelite/service/FeatureToggleService.java`
   - `src/test/java/org/tradelite/service/FeatureToggleServiceTest.java`
   - `config/feature-toggles.json`

### Previous: SQLite Integration - COMPLETED (Feb 18, 2026)

1. **New Repository Layer** ✅
   - `PriceQuoteRepository` interface - defines persistence contract
   - `SqlitePriceQuoteRepository` implementation with JDBC
   - `PriceQuoteEntity` - entity class for stored quotes

2. **Database Schema** ✅
   ```sql
   CREATE TABLE finnhub_price_quotes (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       symbol TEXT NOT NULL,
       timestamp INTEGER NOT NULL,  -- UTC epoch seconds
       current_price REAL NOT NULL,
       ...
       UNIQUE(symbol, timestamp)
   )
   ```

3. **Integration** ✅
   - `FinnhubPriceEvaluator` saves every price quote to SQLite
   - Data stored in `data/tradebot.db`

### Previous: Relative Strength vs SPY - COMPLETED (Feb 16, 2026)
- TradingView-style RS indicator comparing stocks to SPY benchmark
- 50-period EMA crossover detection for alerts

### Previous: Sector Rotation Detection - COMPLETED (Feb 15, 2026)
- Z-Score based statistical analysis for early rotation detection
- High-confidence alerts (>2 standard deviations)

## Testing Summary

- **Total Tests**: 424 ✅ (increased from 407)
- **New Tests Added**: 17 (FeatureToggleServiceTest)
- **Build Status**: SUCCESS ✅
- **Coverage**: All checks met (≥97%) ✅

## Active Decisions and Considerations

### Feature Toggle Design Decisions
- **JSON file storage**: Simple, human-readable, easy to edit
- **3-minute cache TTL**: Balance between responsiveness and file I/O
- **Default to false**: Unknown toggles return false (fail-safe)
- **Configurable file path**: For testability with temp directories

### Future Feature Toggle Use Cases
- Demo trading mode toggle
- Enable/disable specific alerts
- A/B testing features
- Gradual feature rollouts

## Configuration Files
- `config/stock-symbols.json` - Stock symbol registry (38 symbols)
- `config/target-prices-stocks.json` - Stock target prices
- `config/target-prices-coins.json` - Crypto target prices
- `config/sector-performance.json` - Sector performance history
- `config/insider-transactions.json` - Insider trading data
- `config/feature-toggles.json` - **NEW** Runtime feature flags
- `data/tradebot.db` - SQLite database for price history

## Next Iteration Opportunities

### Feature Toggle Enhancements (Future)
- Add Telegram command to view/modify toggles
- Add toggle change notifications
- Support non-boolean toggle values (strings, numbers)
- Toggle expiration/scheduling

### SQLite Enhancements (Future)
- Migrate existing JSON persistence to SQLite
- Add CoinGecko price persistence
- Data retention/cleanup policies