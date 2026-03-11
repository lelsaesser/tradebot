# Active Context

## Current Work Focus

The Tail Risk (Kurtosis) Analysis feature has been implemented as a new quantitative analysis capability. This introduces the new `quant` package for advanced statistical analysis beyond the existing sector rotation metrics.

## Recent Changes

### Tail Risk Analysis Feature Implementation (Completed - March 11, 2026)
1. **New `quant` Package Created:**
   - `TailRiskLevel` - Enum for risk classifications (LOW, MODERATE, HIGH, EXTREME)
   - `TailRiskAnalysis` - Record for storing analysis results
   - `TailRiskService` - Service calculating excess kurtosis from daily price changes
   - `TailRiskTracker` - Component monitoring sector ETFs + SPY for fat tail risk

2. **Repository Enhancement:**
   - Added `findDailyChangePercents(symbol, days)` to `PriceQuoteRepository`
   - Returns list of daily change percentages for kurtosis calculation

3. **Integration:**
   - Added `TailRiskTracker` to `Scheduler` with dedicated daily task at 10:00 AM CET
   - Only alerts when HIGH or EXTREME risk detected (not daily reports)

4. **How It Works:**
   - Calculates excess kurtosis from historical daily price changes (last 25 days)
   - Kurtosis > 3 (excess > 0) indicates "fat tails" - higher probability of extreme moves
   - Risk levels: LOW (< 1.0), MODERATE (1.0-3.0), HIGH (3.0-6.0), EXTREME (≥ 6.0)
   - Monitors 12 sector ETFs: SPY, XLE, XLK, XLF, XLV, XLI, XLP, XLY, XLB, XLU, XLRE, XLC

5. **Tests Added:**
   - `TailRiskServiceTest` - 9 tests covering kurtosis calculation and edge cases
   - `TailRiskTrackerTest` - 10 tests covering alert generation and reporting

### Kurtosis Formula
```
Kurtosis = n * Σ(xi - x̄)⁴ / (Σ(xi - x̄)²)²
Excess Kurtosis = Kurtosis - 3  (normal distribution has kurtosis = 3)
```

### Alert Interpretation
- **High Excess Kurtosis**: Indicates "fat tails" - extreme price moves more likely than normal
- **Direction Agnostic**: High kurtosis doesn't predict direction (could be crash OR rally)
- **Context Required**: Alert recommends reviewing macro conditions for direction assessment

## Four-Pronged Statistical Analysis

The system now uses four complementary approaches:

1. **Z-Score Analysis** (via FinViz data)
   - Statistical deviation from historical performance
   - Detects extreme moves in sector performance

2. **Relative Strength vs SPY** 
   - RS = Sector Price / SPY Price ratio
   - EMA crossover detection (50-period)
   - Shows momentum vs benchmark

3. **Momentum ROC**
   - Rate of Change over 10 and 20 days
   - Zero-line crossover detection
   - Shows pure momentum direction change

4. **Tail Risk (Kurtosis)** (NEW)
   - Measures probability of extreme moves
   - Fat tail detection via excess kurtosis
   - Early warning for volatile conditions

## Technical Patterns

### Kurtosis Calculation
```java
// Step 1: Calculate mean
double mean = changes.stream().mapToDouble(d -> d).average().orElse(0);

// Step 2: Calculate second (variance) and fourth moments
double sumSquared = 0, sumFourth = 0;
for (double change : changes) {
    double diff = change - mean;
    sumSquared += diff * diff;
    sumFourth += diff * diff * diff * diff;
}

// Step 3: Kurtosis formula
double kurtosis = (n * sumFourth) / (sumSquared * sumSquared);
double excessKurtosis = kurtosis - 3.0;
```

### Risk Level Classification
| Excess Kurtosis | Risk Level | Meaning |
|-----------------|------------|---------|
| < 1.0 | LOW 🟢 | Normal tail risk |
| 1.0 - 3.0 | MODERATE 🟡 | Slightly elevated |
| 3.0 - 6.0 | HIGH 🟠 | Significant fat tails |
| ≥ 6.0 | EXTREME 🔴 | Crash/rally risk elevated |

### Minimum Data Requirements
- 20 data points minimum for reliable kurtosis calculation
- Default 25 days fetched for analysis

## Next Steps

Potential enhancements:
- Add historical kurtosis tracking for trend detection
- Consider VIX integration for additional context
- Add skewness calculation for directional bias hints