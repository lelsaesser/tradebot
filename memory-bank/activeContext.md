# Active Context

## Current Work Focus

The Momentum ROC (Rate of Change) feature has been implemented as the third sector rotation detection approach. This complements the existing Z-score analysis and relative strength tracking.

## Recent Changes

### Momentum ROC Feature Implementation (Completed)
1. **Core Classes Created:**
   - `MomentumRocSignal` - Record for storing momentum crossover signals
   - `MomentumRocData` - Model for persisting ROC state between calculations  
   - `MomentumRocRepository` - Interface for SQLite persistence
   - `SqliteMomentumRocRepository` - SQLite implementation with state tracking
   - `MomentumRocService` - Service calculating ROC and detecting zero-line crossovers
   - `SectorMomentumRocTracker` - Component monitoring all 11 SPDR sector ETFs

2. **Integration:**
   - Added `SectorMomentumRocTracker` to `Scheduler.runDailySectorRotationTracking()`
   - Uses existing price data from SQLite via `PriceQuoteRepository`

3. **How It Works:**
   - Calculates ROC10 (10-day) and ROC20 (20-day) momentum from historical prices
   - Detects zero-line crossovers (momentum turning positive/negative)
   - Persists previous ROC values to detect crossovers between daily runs
   - Sends Telegram alerts when crossovers occur

4. **Tests Added:**
   - `MomentumRocSignalTest` - 10 tests
   - `MomentumRocDataTest` - 7 tests
   - `SqliteMomentumRocRepositoryTest` - 7 tests
   - `MomentumRocServiceTest` - 27 tests
   - `SectorMomentumRocTrackerTest` - 10 tests

### Test Coverage Improvements
- Added 9 tests to `SectorRelativeStrengthTrackerTest` covering `analyzeAndSendAlerts()`
- Total tests: 539 (up from ~509)
- Coverage now meets 97% threshold

## Three-Pronged Sector Rotation Detection

The system now uses three complementary approaches:

1. **Z-Score Analysis** (via FinViz data)
   - Statistical deviation from historical performance
   - Detects extreme moves in sector performance

2. **Relative Strength vs SPY** 
   - RS = Sector Price / SPY Price ratio
   - EMA crossover detection (50-period)
   - Shows momentum vs benchmark

3. **Momentum ROC** (NEW)
   - Rate of Change over 10 and 20 days
   - Zero-line crossover detection
   - Shows pure momentum direction change

## Technical Patterns

### ROC Formula
```
ROC = ((Current Price - Price N days ago) / Price N days ago) × 100
```

### Signal Detection
- **Momentum Turning Positive**: Previous ROC10 < 0 AND Current ROC10 >= 0
- **Momentum Turning Negative**: Previous ROC10 >= 0 AND Current ROC10 < 0

### Minimum Data Requirements
- 21 data points minimum for ROC calculation
- 35 days fetched to ensure sufficient history

## Next Steps

Potential enhancements:
- Add ROC divergence detection (price vs ROC)
- Consider ROC20 crossover for longer-term signals
- Add configurable thresholds (e.g., only signal if ROC > ±2%)