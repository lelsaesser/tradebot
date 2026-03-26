# Active Context

## Current Work Focus
Implemented Bollinger Band analysis for sector ETFs and extracted shared statistical utilities. Also evaluated finmath-lib external library.

## Recent Changes (March 26, 2026)

### Bollinger Band Analysis ✅ COMPLETE
- **`StatisticsUtil`** — shared utility with `mean()`, `standardDeviation()`, `zScore()`, `percentileRank()` used across 3 services
- **`BollingerSignalType`** — enum: UPPER_BAND_TOUCH, LOWER_BAND_TOUCH, SQUEEZE
- **`BollingerBandAnalysis`** — record with %B, bandwidth, signals, interpretation methods
- **`BollingerBandService`** — 20-period SMA ± 2σ bands, %B calculation, bandwidth percentile, squeeze detection
- **`BollingerBandTracker`** — orchestrator sending Telegram alerts for signals and daily summary reports
- **Refactored**: `SectorRotationAnalyzer` and `TailRiskService` to use `StatisticsUtil` instead of inline math

### finmath-lib Evaluation ✅ DECIDED: NOT ADDING
- Evaluated https://github.com/finmath/finmath-lib for potential integration
- Library is a heavy academic framework (derivatives pricing, Monte Carlo, interest rate models)
- Our lightweight custom implementations (`StatisticsUtil`, `TailRiskService`, `BollingerBandService`) are better suited
- Purpose-built, zero-dependency, and cover exactly what the tradebot needs

### All 658 tests passing ✅

## Architecture Decisions
- **Shared `StatisticsUtil`**: Eliminates statistical code duplication across `SectorRotationAnalyzer`, `TailRiskService`, and `BollingerBandService`
- **No external quant library**: Custom implementations preferred over finmath-lib to keep dependency footprint small and code purpose-built
- **`quant` package**: All quantitative analysis components (`TailRisk*`, `BollingerBand*`, `StatisticsUtil`, `SkewnessLevel`) live in `org.tradelite.quant`

## Next Steps
- Wire `BollingerBandTracker` into `Scheduler` for automated daily/real-time execution
- Consider MACD indicator as next quant feature (uses same EMA concepts)
- Consider combining Bollinger + RS + ROC signals for multi-signal confirmation alerts
- Monitor API rate limits with 20 ETFs across all tracking systems