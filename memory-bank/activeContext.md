# Active Context

## Current Work Focus
✅ **COMPLETED**: Momentum ROC Sector Rotation Enhancement (February 26, 2026)
- Added Rate of Change (ROC) momentum analysis for sector ETFs
- Real-time crossover detection during market hours
- Enhanced SectorRelativeStrengthTracker with real-time RS alerts
- Three-pronged sector rotation detection now active

## Recent Changes (February 2026)

### Momentum ROC Sector Tracking - COMPLETED (Feb 26, 2026)

1. **New Core Components** ✅
   - `MomentumRocSignal` - Record for momentum signals with ROC10/ROC20 values
   - `MomentumRocData` - Model for persisting previous ROC values
   - `MomentumRocRepository` - Interface for ROC state persistence
   - `SqliteMomentumRocRepository` - SQLite implementation for momentum state
   - `MomentumRocService` - Calculates ROC and detects zero-line crossovers
   - `SectorMomentumRocTracker` - Orchestrates sector analysis and Telegram alerts

2. **ROC Calculation** ✅
   - **ROC Formula**: `((Current Price - Price N days ago) / Price N days ago) × 100`
   - **ROC10**: 10-trading-day momentum (short-term)
   - **ROC20**: 20-trading-day momentum (longer-term)
   - **Crossover Detection**: Alerts when ROC10 crosses the zero line

3. **Signal Types** ✅
   - `MOMENTUM_TURNING_POSITIVE` - ROC crossed from negative to positive (bullish)
   - `MOMENTUM_TURNING_NEGATIVE` - ROC crossed from positive to negative (bearish)

4. **SQLite Persistence** ✅
   - New table `momentum_roc_state` stores previous ROC values
   - Enables crossover detection across monitoring cycles
   - Auto-schema creation on startup

5. **Real-Time Monitoring** ✅
   - Runs every 5 minutes during market hours (part of `stockMarketMonitoring`)
   - Analyzes all 11 SPDR sector ETFs
   - Sends Telegram alert only when crossover detected

6. **Files Created** ✅
   - `src/main/java/org/tradelite/core/MomentumRocSignal.java` (NEW)
   - `src/main/java/org/tradelite/service/model/MomentumRocData.java` (NEW)
   - `src/main/java/org/tradelite/repository/MomentumRocRepository.java` (NEW)
   - `src/main/java/org/tradelite/repository/SqliteMomentumRocRepository.java` (NEW)
   - `src/main/java/org/tradelite/service/MomentumRocService.java` (NEW)
   - `src/main/java/org/tradelite/core/SectorMomentumRocTracker.java` (NEW)

### Real-Time Sector RS Alerts - COMPLETED (Feb 26, 2026)

1. **Enhanced SectorRelativeStrengthTracker** ✅
   - Added `analyzeAndSendAlerts()` method for real-time RS crossover detection
   - Detects when sector ETF RS crosses above/below 50-period EMA
   - Sends "📊 SECTOR RS CROSSOVER ALERT" messages
   - Now provides both daily summary AND real-time crossover alerts

2. **Files Modified** ✅
   - `src/main/java/org/tradelite/core/SectorRelativeStrengthTracker.java` (MODIFIED)
   - `src/main/java/org/tradelite/Scheduler.java` (MODIFIED)
   - `src/test/java/org/tradelite/SchedulerTest.java` (MODIFIED)

### Three-Pronged Sector Rotation Detection

| Approach | Signal Type | Schedule | Data Source |
|----------|-------------|----------|-------------|
| **Z-Score Analysis** | Industry performance anomalies | Daily (after market) | Finviz |
| **Relative Strength vs SPY** | RS EMA crossovers | Real-time (5 min) | SQLite price data |
| **Momentum ROC** | Zero-line crossovers | Real-time (5 min) | SQLite price data |

### Stock Market Monitoring Flow

```java
@Scheduled(initialDelay = 0, fixedRate = 300000)
protected void stockMarketMonitoring() {
    if (DateUtil.isStockMarketOpen(dayOfWeek, localTime)) {
        rootErrorHandler.run(finnhubPriceEvaluator::evaluatePrice);
        // Real-time sector rotation signals
        rootErrorHandler.run(sectorRelativeStrengthTracker::analyzeAndSendAlerts);
        rootErrorHandler.run(sectorMomentumRocTracker::analyzeAndSendAlerts);
    }
}
```

## Testing Summary

- **Total Tests**: 466 ✅
- **Build Status**: SUCCESS ✅
- **All Scheduler tests updated for new constructor parameter**

## Active Decisions and Considerations

### Momentum ROC Design Decisions
- **SQLite persistence**: Previous ROC values stored to detect crossovers across cycles
- **ROC10 for signals**: Short-term 10-day ROC used for crossover detection
- **ROC20 for context**: 20-day ROC included in alerts for trend confirmation
- **Minimum 21 data points**: Requires sufficient history for reliable calculation
- **35-day lookback**: Calendar days with buffer for weekends/holidays

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
- `data/tradebot.db` - SQLite database (price history + momentum ROC state)

## Scheduled Tasks

| Task | Schedule | Description |
|------|----------|-------------|
| stockMarketMonitoring | Every 5 min (9:30-16:00 ET) | Stock prices + RS alerts + ROC alerts |
| dailySectorRotationTracking | Daily 22:30 ET (Mon-Fri) | Sector performance + Z-score rotation alerts |
| dailySectorRsSummary | Daily 12:00 CET (Mon-Fri) | Sector RS vs SPY daily summary |
| rsiStockMonitoring | Daily 23:00 CET (Mon-Fri) | RSI + Relative Strength analysis |

## Alert Message Formats

### Momentum ROC Alert
```
⚡ *SECTOR MOMENTUM ROC ALERT*

📈 *MOMENTUM TURNING POSITIVE:*
• *Technology* (XLK): ROC₁₀ +2.5% | ROC₂₀ +1.8%

📉 *MOMENTUM TURNING NEGATIVE:*
• *Energy* (XLE): ROC₁₀ -1.2% | ROC₂₀ -0.5%

_ROC₁₀ = 10-day momentum | ROC₂₀ = 20-day momentum_
```

### Sector RS Crossover Alert
```
📊 *SECTOR RS CROSSOVER ALERT*

*🟢 NOW OUTPERFORMING SPY:*
• *Technology* (XLK): +3.5%

*🔴 NOW UNDERPERFORMING SPY:*
• *Utilities* (XLU): -2.1%

_RS crossed 50-period EMA_
```

## Next Iteration Opportunities

### Future Enhancements
- ROC divergence alerts (when ROC10 and ROC20 diverge)
- Sector strength ranking in ROC alerts
- Combine RS and ROC signals for stronger confirmation
- Historical crossover tracking for backtesting