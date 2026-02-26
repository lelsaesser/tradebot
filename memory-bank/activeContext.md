# Active Context

## Current Work Focus
✅ **COMPLETED**: Daily Sector RS Summary Feature (February 26, 2026)
- Daily Telegram report showing sector ETF performance vs SPY benchmark
- Uses SQLite historical price data for relative strength calculation
- Shows RS value, percentage vs benchmark, and data completeness status
- Scheduled daily at 22:35 ET (Mon-Fri)

## Recent Changes (February 2026)

### Daily Sector RS Summary - COMPLETED (Feb 26, 2026)

1. **New Core Component** ✅
   - `SectorRelativeStrengthTracker` - Daily sector ETF relative strength summary
   - Monitors all 11 SPDR sector ETFs (XLK, XLF, XLE, XLV, XLY, XLP, XLI, XLC, XLRE, XLB, XLU)
   - Calculates RS (Relative Strength) vs SPY benchmark from SQLite data
   - Sends formatted Telegram summary sorted by performance

2. **Data Model** ✅
   - `RsResult` record in `RelativeStrengthService`:
     - `rs` - Current relative strength value (stock_price/SPY_price)
     - `ema` - 50-period EMA of RS values
     - `dataPoints` - Number of days of data available
     - `isComplete` - Whether full 50 data points available
   - `SectorRsData` record for formatted output

3. **Repository Enhancement** ✅
   - `findDailyClosingPrices(symbol, days)` method added to `PriceQuoteRepository`
   - Groups intraday prices to get one closing price per day
   - Returns chronologically ordered `DailyPrice` list

4. **Summary Message Format** ✅
   ```
   📊 Daily Sector RS Summary (Feb 26)
   
   XLK: RS 1.12 | +12.0% ✅
   XLF: RS 1.05 | +5.0% ✅
   XLY: RS 0.98 | -2.0% ✅
   XLE: RS 0.92 | -8.0% (32 days)
   ...
   
   ✅ = outperforming SPY | (N days) = incomplete data
   ```

5. **Files Created/Modified** ✅
   - `src/main/java/org/tradelite/core/SectorRelativeStrengthTracker.java` (NEW)
   - `src/test/java/org/tradelite/core/SectorRelativeStrengthTrackerTest.java` (NEW)
   - `src/main/java/org/tradelite/service/RelativeStrengthService.java` (MODIFIED)
   - `src/main/java/org/tradelite/repository/PriceQuoteRepository.java` (MODIFIED)
   - `src/main/java/org/tradelite/repository/SqlitePriceQuoteRepository.java` (MODIFIED)
   - `src/main/java/org/tradelite/Scheduler.java` (MODIFIED)

6. **Schedule** ✅
   - Daily at 22:35 ET, Monday-Friday (after sector rotation tracking at 22:30)
   - Cron: `0 35 22 * * MON-FRI`

### Test Coverage Improvement (Feb 26, 2026)

Added 23 new tests to improve coverage:

**SectorRelativeStrengthTrackerTest (6 tests):**
- Incomplete data shows "(32 days)" indicator
- Mixed complete/incomplete data formatting
- Empty result list handling
- Zero percentage edge case
- All 11 sector names verification
- SectorRsData record fields

**RelativeStrengthServiceTest (10 tests):**
- getCurrentRsResult with complete data (50+ days)
- getCurrentRsResult with incomplete data
- getCurrentRsResult with no SPY data
- getCurrentRsResult with mismatched dates
- getCurrentRsResult returns empty for benchmark
- Exact minimum/full data boundary tests
- RsResult record methods

**SqlitePriceQuoteRepositoryTest (7 tests):**
- findDailyClosingPrices for unknown symbol
- Price grouping by date
- Chronological ordering
- Days limit enforcement
- Symbol filtering
- Error handling

### Previous: Feature Toggle System - COMPLETED (Feb 18, 2026)

- JSON-based runtime feature toggles without restart
- 3-minute cache TTL for dynamic updates
- `FeatureToggleService` with `isEnabled(String)` API

### Previous: SQLite Integration - COMPLETED (Feb 18, 2026)

- SQLite database for historical price data
- Repository pattern with interface and implementation
- Auto-schema creation on startup

## Testing Summary

- **Total Tests**: 466 ✅ (increased from 424)
- **New Tests Added**: 42 (23 for sector RS feature + test improvements)
- **Build Status**: SUCCESS ✅
- **Coverage**: All checks met (≥97%) ✅

## Active Decisions and Considerations

### Sector RS Summary Design Decisions
- **SQLite-based calculation**: Uses persisted historical prices, not in-memory RsiService data
- **Minimum 20 days**: Requires at least 20 data points for meaningful RS
- **50-day complete threshold**: Shows "(N days)" for incomplete data
- **Performance sorting**: Summary sorted best to worst performers
- **Checkmark indicator**: ✅ for outperforming SPY (RS > 1.0)

### Sector ETFs Monitored
All 11 Select Sector SPDR ETFs:
- XLK (Technology), XLF (Financials), XLE (Energy)
- XLV (Health Care), XLY (Consumer Discretionary), XLP (Consumer Staples)
- XLI (Industrials), XLC (Communication Services), XLRE (Real Estate)
- XLB (Materials), XLU (Utilities)

## Configuration Files
- `config/stock-symbols.json` - Stock symbol registry (49 symbols with sector ETFs)
- `config/target-prices-stocks.json` - Stock target prices
- `config/target-prices-coins.json` - Crypto target prices
- `config/sector-performance.json` - Sector performance history
- `config/insider-transactions.json` - Insider trading data
- `config/feature-toggles.json` - Runtime feature flags
- `data/tradebot.db` - SQLite database for price history

## Scheduled Tasks

| Task | Schedule | Description |
|------|----------|-------------|
| stockMarketMonitoring | Every 5 min (9:30-16:00 ET) | Stock prices + SQLite storage |
| dailySectorRotationTracking | Daily 22:30 ET (Mon-Fri) | Sector performance + rotation alerts |
| **dailySectorRsSummary** | Daily 22:35 ET (Mon-Fri) | **NEW** Sector RS vs SPY summary |
| rsiStockMonitoring | Daily 16:30 ET (Mon-Fri) | RSI + Relative Strength analysis |

## Next Iteration Opportunities

### Enhancements (Future)
- Telegram command to request sector RS summary on-demand
- Trend indicators (up/down arrows based on RS direction)
- Weekly sector performance comparison
- Sector rotation alerts based on RS crossovers