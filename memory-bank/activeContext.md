# Active Context

## Current Work Focus
✅ **COMPLETED**: Sector Rotation Detection Algorithm (February 15, 2026)
- Z-Score based statistical analysis for early rotation detection
- High-confidence alerts (>2 standard deviations)
- Telegram notifications for money flow signals
- All 347 tests passing, build successful

## Recent Changes (February 2026)

### Sector Rotation Detection Algorithm - COMPLETED (Feb 15, 2026)

1. **RotationSignal Record** ✅
   - `SignalType`: ROTATING_IN (money flowing in) or ROTATING_OUT (money flowing out)
   - `Confidence`: HIGH (both weekly/monthly > 2σ), MEDIUM, LOW
   - Includes z-scores for weekly and monthly performance

2. **SectorRotationAnalyzer** ✅
   - Z-Score statistical analysis: `(current_value - mean) / std_dev`
   - Calculates historical mean and standard deviation for each sector
   - Only generates HIGH confidence signals (both weekly and monthly z-scores > 2.0)
   - Requires same-direction z-scores (no diverging signals)
   - Minimum 5 historical snapshots required for reliable analysis

3. **SectorRotationTracker Updates** ✅
   - Added `analyzeAndSendRotationAlerts()` method
   - Telegram alert format with "Money Flowing INTO" and "Money Flowing OUT OF" sections
   - Z-score values included in alerts
   - Integrated with daily fetch workflow

4. **New Files Created** ✅
   - `src/main/java/org/tradelite/core/RotationSignal.java`
   - `src/main/java/org/tradelite/core/SectorRotationAnalyzer.java`
   - `src/test/java/org/tradelite/core/SectorRotationAnalyzerTest.java`

5. **Modified Files** ✅
   - `SectorRotationTracker.java` - Added analyzer integration and alert methods
   - `SectorRotationTrackerTest.java` - Added 5 new tests for rotation alerts

### Alert Message Format
```
🚨 *SECTOR ROTATION ALERT*

*💰 Money Flowing INTO:*
• *Technology*
  Weekly: +15.00% (z=2.5) | Monthly: +25.00% (z=3.0)

*💸 Money Flowing OUT OF:*
• *Energy*
  Weekly: -12.00% (z=-2.8) | Monthly: -18.00% (z=-3.2)

_Based on Z-Score analysis (>2σ deviation)_
```

### Previous: Sector Rotation Tracking Base - COMPLETED (Feb 9, 2026)

1. **FinViz Web Scraper** ✅
   - Created `FinvizClient` using JSoup for HTML parsing
   - Scrapes industry performance data from FinViz
   - No browser automation needed (pure HTTP + HTML parsing)

2. **Data Persistence** ✅
   - `SectorPerformancePersistence` stores snapshots in JSON
   - Historical data maintained for trend analysis

3. **Automated Tracking** ✅
   - Scheduled daily at 10:30 PM ET (after US market close)
   - Sends daily Telegram report with top 5 gainers and losers

## Testing Summary

- **Total Tests**: 347 ✅ (increased from 330)
- **New Tests Added**: 17 (12 SectorRotationAnalyzerTest + 5 SectorRotationTrackerTest)
- **Build Status**: SUCCESS ✅
- **Coverage**: All checks met ✅

## Active Decisions and Considerations

### Algorithm Design Decisions
- **Z-Score over Moving Averages**: Provides adaptive thresholds that adjust to market volatility
- **Conservative Approach**: Only HIGH confidence signals (>2σ) to minimize false positives
- **Dual Timeframe**: Requires both weekly AND monthly z-scores to confirm rotation
- **Same-Direction Requirement**: No alerts for diverging weekly/monthly signals

### Detection Thresholds
```java
HIGH_CONFIDENCE_THRESHOLD = 2.0   // 2 standard deviations
MEDIUM_CONFIDENCE_THRESHOLD = 1.5 // 1.5 standard deviations
MIN_HISTORY_SIZE = 5              // Minimum snapshots needed
```

### Data Flow
```
FinvizClient -> SectorPerformancePersistence -> SectorRotationAnalyzer -> TelegramClient
     |                    |                            |
  Fetch data         Store snapshot              Analyze z-scores
                                                      |
                                              Generate RotationSignal
                                                      |
                                              Send Telegram alerts
```

## Important Patterns and Preferences

### Z-Score Calculation Pattern
```java
double zScore = (currentValue - historicalMean) / stdDev;
// Positive z-score = outperforming historical norm
// Negative z-score = underperforming historical norm
```

### Rotation Signal Generation
```java
// Only generate signal if:
// 1. Both z-scores have same direction (both positive or both negative)
// 2. Both z-scores exceed HIGH_CONFIDENCE_THRESHOLD (2.0)
// 3. Sector has enough historical data (>= 5 snapshots)
```

### Defensive Coding Pattern (NPE Prevention)
```java
int weeklySize = weekly == null ? 0 : weekly.size();
int monthlySize = monthly == null ? 0 : monthly.size();
```

## Configuration Files
- `config/stock-symbols.json` - Stock symbol registry (38 symbols)
- `config/target-prices-stocks.json` - Stock target prices
- `config/target-prices-coins.json` - Crypto target prices
- `config/sector-performance.json` - Sector performance history
- `config/insider-transactions.json` - Insider trading data

## Next Iteration Opportunities

### Sector Rotation Enhancements (Future)
- Add MEDIUM confidence alerts option (configurable)
- Momentum scoring (rate of change in z-scores)
- Multi-week trend confirmation
- Sector correlation analysis
- Historical backtesting of signals

### General Enhancements
- Bulk import/export of stock symbols
- Rate limiting for API calls
- Performance optimizations