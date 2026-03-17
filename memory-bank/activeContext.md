# Active Context

## Current Work Focus

The Tail Risk Analysis feature has been enhanced with **Skewness calculation** to provide directional bias alongside kurtosis. This completes the original tail risk implementation by adding the ability to indicate whether extreme moves lean toward crashes or rallies.

## Recent Changes

### Skewness Enhancement to Tail Risk (Completed - March 17, 2026)

1. **New Component Created:**
   - `SkewnessLevel` - Enum for skewness classifications (HIGHLY_NEGATIVE, NEGATIVE, NEUTRAL, POSITIVE, HIGHLY_POSITIVE)

2. **Enhanced Components:**
   - `TailRiskAnalysis` - Added `skewness` and `skewnessLevel` fields
   - `TailRiskLevel` - Added `isElevated()` method for HIGH/EXTREME detection
   - `TailRiskService` - Added `calculateSkewness()` method
   - `TailRiskTracker` - Enhanced alert messages with directional bias information

3. **How Skewness Works:**
   - **Negative Skewness**: Left tail is fatter → crashes more likely than rallies
   - **Positive Skewness**: Right tail is fatter → rallies more likely than crashes  
   - **Zero Skewness**: Symmetric distribution → balanced risk

4. **Combined Risk Assessment:**
   - **Kurtosis**: Tells you *how likely* extreme moves are
   - **Skewness**: Tells you *which direction* they're more likely to go

5. **Tests Updated:**
   - `TailRiskServiceTest` - 19 tests (added 10 new skewness tests)
   - `TailRiskTrackerTest` - 13 tests (updated for new message format)

### Skewness Formula
```
Skewness = (1/n) * Σ((xi - x̄)³) / σ³

Normal distribution: skewness = 0
Negative skew: < 0 (crash bias)
Positive skew: > 0 (rally bias)
```

### Skewness Level Classification
| Skewness | Level | Emoji | Meaning |
|----------|-------|-------|---------|
| < -1.0 | HIGHLY_NEGATIVE | ⬇️⬇️ | Strong crash bias |
| -1.0 to -0.5 | NEGATIVE | ⬇️ | Moderate downside skew |
| -0.5 to +0.5 | NEUTRAL | ↔️ | No directional bias |
| +0.5 to +1.0 | POSITIVE | ⬆️ | Moderate upside skew |
| > +1.0 | HIGHLY_POSITIVE | ⬆️⬆️ | Strong rally bias |

### Enhanced Alert Format
```
🔴 *Tail Risk Alert - Extreme*

*Extreme* risk sectors:
• *Energy* (XLE): Kurtosis 10.5 | Skew -1.2 ⬇️⬇️
   _Fat tails with strong crash bias_

📊 *Directional Bias:*
• ⬇️ 1 sector(s) with crash risk bias
• ⬆️ 1 sector(s) with rally potential

_Kurtosis = probability of extreme moves_
_Skewness = likely direction (⬇️ crash / ⬆️ rally)_
```

### Combined Interpretation
| Kurtosis | Skewness | Interpretation |
|----------|----------|----------------|
| HIGH/EXTREME | Negative | Fat tails with crash bias - **defensive posture** |
| HIGH/EXTREME | Positive | Fat tails with rally bias - **opportunity window** |
| HIGH/EXTREME | Neutral | Fat tails, uncertain direction - **high volatility** |
| LOW/MODERATE | Any | Normal market conditions |

## Four-Pronged Statistical Analysis

The system uses four complementary approaches:

| Approach | Component | Signal | Schedule |
|----------|-----------|--------|----------|
| **Z-Score Analysis** | `SectorRotationAnalyzer` | Industry performance anomalies | Daily (after market) |
| **Relative Strength vs SPY** | `SectorRelativeStrengthTracker` | RS EMA crossovers | Real-time (5 min) |
| **Momentum ROC** | `SectorMomentumRocTracker` | Zero-line crossovers | Real-time (5 min) |
| **Tail Risk (Kurtosis + Skewness)** | `TailRiskTracker` | Fat tail + directional bias | Daily 10:00 CET |

## Technical Patterns

### Kurtosis Calculation
```java
double kurtosis = (n * sumFourth) / (sumSquared * sumSquared);
double excessKurtosis = kurtosis - 3.0;
```

### Skewness Calculation
```java
double skewness = (sumCubedDiff / n) / (stdDev * stdDev * stdDev);
```

### Risk Level Classification
| Excess Kurtosis | Risk Level | Meaning |
|-----------------|------------|---------|
| < 1.0 | LOW 🟢 | Normal tail risk |
| 1.0 - 3.0 | MODERATE 🟡 | Slightly elevated |
| 3.0 - 6.0 | HIGH 🟠 | Significant fat tails |
| ≥ 6.0 | EXTREME 🔴 | Crash/rally risk elevated |

## Quant Package Overview

```
src/main/java/org/tradelite/quant/
├── SkewnessLevel.java     # Enum: directional bias classification
├── TailRiskLevel.java     # Enum: risk level classification
├── TailRiskAnalysis.java  # Record: analysis results (kurtosis + skewness)
├── TailRiskService.java   # Service: kurtosis & skewness calculation
└── TailRiskTracker.java   # Component: sector monitoring & alerts
```

## Next Steps

Potential future enhancements:
- Historical kurtosis/skewness tracking for trend detection
- VIX integration for additional volatility context
- Sharpe/Sortino ratio calculations
- Combine tail risk with other signals for confirmation alerts