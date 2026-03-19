# Active Context

## Current Work Focus

**Sector RS Streak Tracking** has been implemented to track consecutive days of outperformance or underperformance vs SPY for each sector ETF. This provides at-a-glance visibility into whether a sector's strength/weakness is a short-term anomaly or a sustained trend.

## Recent Changes

### Sector RS Streak Tracking (Completed - March 19, 2026)

1. **New Components Created:**
   - `SectorRsStreak` - Record tracking symbol, streak days, direction (outperforming/underperforming), and last updated date
   - `SectorRsStreakPersistence` - JSON file persistence for streak data across application restarts

2. **Enhanced Components:**
   - `SectorRelativeStrengthTracker` - Integrated streak tracking into the daily sector RS report

3. **How Streak Tracking Works:**
   - **New Sector**: Streak starts at day 1
   - **Same Direction**: Streak increments each day the sector maintains same performance direction
   - **Direction Change**: Streak resets to day 1 with new direction
   - **Same Day Updates**: Returns existing streak (no double-counting)

4. **Daily Report Format:**
   ```
   📊 *Sector Relative Strength Report*

   🟢 *Outperforming SPY:*
   • XLK (Tech): RS 1.05 | EMA 1.03 🟢5
   • XLF (Finance): RS 1.02 | EMA 1.01 🟢2

   🔴 *Underperforming SPY:*
   • XLU (Utilities): RS 0.95 | EMA 0.97 🔴12
   • XLE (Energy): RS 0.92 | EMA 0.94 🔴3
   ```
   - `🟢5` = 5 consecutive days outperforming SPY
   - `🔴12` = 12 consecutive days underperforming SPY

5. **Value Proposition:**
   - Immediately see if sector outperformance is short-term (1-2 days) or sustained (10+ days)
   - Long streaks indicate strong trends worth paying attention to
   - Short streaks may be noise/mean reversion candidates

6. **Tests Added:**
   - `SectorRsStreakTest` - 9 tests for streak record behavior
   - `SectorRsStreakPersistenceTest` - 14 tests for persistence logic
   - `SectorRelativeStrengthTrackerTest` - Updated existing tests for streak integration

### Files Created
```
src/main/java/org/tradelite/core/
├── SectorRsStreak.java              # Streak data record
└── SectorRsStreakPersistence.java   # JSON file persistence

config/
└── sector-rs-streaks.json           # Streak data file (created at runtime)

src/test/java/org/tradelite/core/
├── SectorRsStreakTest.java          # Unit tests for record
└── SectorRsStreakPersistenceTest.java # Integration tests for persistence
```

### Streak Indicator Format
| Streak | Indicator | Meaning |
|--------|-----------|---------|
| 1 day outperforming | 🟢1 | Just started outperforming |
| 5 days outperforming | 🟢5 | Sustained outperformance |
| 1 day underperforming | 🔴1 | Just started underperforming |
| 15 days underperforming | 🔴15 | Long-term weakness |

## Four-Pronged Statistical Analysis

The system uses four complementary approaches:

| Approach | Component | Signal | Schedule |
|----------|-----------|--------|----------|
| **Z-Score Analysis** | `SectorRotationAnalyzer` | Industry performance anomalies | Daily (after market) |
| **Relative Strength vs SPY** | `SectorRelativeStrengthTracker` | RS EMA crossovers + **streak tracking** | Real-time (5 min) |
| **Momentum ROC** | `SectorMomentumRocTracker` | Zero-line crossovers | Real-time (5 min) |
| **Tail Risk (Kurtosis + Skewness)** | `TailRiskTracker` | Fat tail + directional bias | Daily 10:00 CET |

## Technical Patterns

### Streak Update Logic
```java
if (currentStreak == null) {
    // First time tracking this sector
    return SectorRsStreak.newStreak(symbol, isOutperforming, date);
} else if (currentStreak.lastUpdated().equals(date)) {
    // Already updated today
    return currentStreak;
} else if (isOutperforming == currentStreak.isOutperforming()) {
    // Same direction - increment streak
    return new SectorRsStreak(symbol, streakDays + 1, isOutperforming, date);
} else {
    // Direction changed - reset to 1
    return new SectorRsStreak(symbol, 1, isOutperforming, date);
}
```

### Streak Persistence Format (JSON)
```json
{
  "XLK": {
    "symbol": "XLK",
    "streakDays": 5,
    "isOutperforming": true,
    "lastUpdated": "2026-03-19"
  },
  "XLU": {
    "symbol": "XLU",
    "streakDays": 12,
    "isOutperforming": false,
    "lastUpdated": "2026-03-19"
  }
}
```

## Configuration

The streak persistence file path can be configured via:
```yaml
tradebot:
  sector-rs-streaks:
    file-path: config/sector-rs-streaks.json  # default
```

## Next Steps

Potential future enhancements:
- Historical streak analysis (longest streaks, average streak length)
- Alert when streak exceeds threshold (e.g., 10+ days)
- Combine streak data with other signals for confirmation
- Track streak reversal patterns