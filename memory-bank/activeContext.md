# Active Context

## Current Work Focus
✅ **COMPLETED**: SQLite Integration for Historical Price Data (February 18, 2026)
- SQLite database for persisting Finnhub price quotes
- Repository pattern with auto-schema initialization
- UTC timestamps for best practice storage
- Integrated with FinnhubPriceEvaluator - every price fetch is stored
- All 407 tests passing, build successful

## Recent Changes (February 2026)

### SQLite Historical Price Data - COMPLETED (Feb 18, 2026)

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
       daily_open REAL,
       daily_high REAL,
       daily_low REAL,
       change_amount REAL,
       change_percent REAL,
       previous_close REAL,
       UNIQUE(symbol, timestamp)
   )
   ```
   - **Indexes**: symbol, timestamp, composite (symbol, timestamp)
   - **Table naming**: `finnhub_price_quotes` for future extensibility (CoinGecko, etc.)

3. **Best Practices Applied** ✅
   - **UTC timestamps**: Epoch seconds are inherently UTC, queries use UTC timezone
   - **Java naming conventions**: No "I" prefix for interfaces (PriceQuoteRepository, not IPriceQuoteRepository)
   - **Auto-schema initialization**: Table and indexes created on repository instantiation
   - **Source-specific table naming**: Allows future `coingecko_price_quotes` table

4. **Integration** ✅
   - `FinnhubPriceEvaluator` saves every price quote to SQLite
   - Runs during stock market monitoring (every 5 min, 9:30-16:00 ET)
   - Data stored in `data/tradebot.db`

5. **New Files Created** ✅
   - `src/main/java/org/tradelite/repository/PriceQuoteEntity.java`
   - `src/main/java/org/tradelite/repository/PriceQuoteRepository.java`
   - `src/main/java/org/tradelite/repository/SqlitePriceQuoteRepository.java`
   - `src/test/java/org/tradelite/repository/SqlitePriceQuoteRepositoryTest.java`

6. **Modified Files** ✅
   - `pom.xml` - Added SQLite JDBC driver (org.xerial:sqlite-jdbc:3.49.0.0)
   - `src/main/java/org/tradelite/config/BeanConfig.java` - Added DataSource bean
   - `src/main/resources/application.yaml` - Added database.path config
   - `src/main/java/org/tradelite/core/FinnhubPriceEvaluator.java` - Repository integration
   - `src/test/java/org/tradelite/core/FinnhubPriceEvaluatorTest.java` - Repository mock
   - `.gitignore` - Added `/data/` and `*.db`

### Previous: Relative Strength vs SPY - COMPLETED (Feb 16, 2026)
- TradingView-style RS indicator comparing stocks to SPY benchmark
- 50-period EMA crossover detection for alerts
- Daily analysis after RSI stock price collection

### Previous: Sector Rotation Detection - COMPLETED (Feb 15, 2026)
- Z-Score based statistical analysis for early rotation detection
- High-confidence alerts (>2 standard deviations)

## Testing Summary

- **Total Tests**: 407 ✅ (increased from 347)
- **New Tests Added**: 17 (SqlitePriceQuoteRepositoryTest)
- **Build Status**: SUCCESS ✅
- **Coverage**: All checks met (≥97%) ✅

## Active Decisions and Considerations

### SQLite Design Decisions
- **UTC for storage**: Epoch seconds are timezone-agnostic, queries use UTC
- **Table per data source**: `finnhub_price_quotes` naming allows future tables
- **Auto-initialization**: Schema created on first use, no migration tool needed yet
- **UNIQUE constraint**: (symbol, timestamp) prevents duplicate entries

### Future Use Cases for Historical Data
- Technical indicators requiring historical prices (MACD, Bollinger Bands, etc.)
- Backtesting trading strategies
- Price pattern analysis
- Correlation studies between symbols

## Configuration Files
- `config/stock-symbols.json` - Stock symbol registry (38 symbols)
- `config/target-prices-stocks.json` - Stock target prices
- `config/target-prices-coins.json` - Crypto target prices
- `config/sector-performance.json` - Sector performance history
- `config/insider-transactions.json` - Insider trading data
- `data/tradebot.db` - **NEW** SQLite database for price history

## Next Iteration Opportunities

### SQLite Enhancements (Future PRs)
- Migrate existing JSON persistence to SQLite
- Add CoinGecko price persistence (coingecko_price_quotes table)
- Implement data retention/cleanup policies
- Add aggregate queries for analysis (daily OHLC, averages, etc.)
- Query endpoints via Telegram commands

### Technical Analysis (Future)
- Historical price-based indicators
- Price pattern recognition
- Volatility analysis using stored data