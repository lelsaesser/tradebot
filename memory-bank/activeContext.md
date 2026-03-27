# Active Context

## Current Work Focus
Extended Bollinger Band tracking to cover all tracked stocks in addition to sector ETFs. The tracker now analyzes both sectors and individual stocks, with separate report sections.

## Recent Changes (March 27, 2026)

### Bollinger Band Stock Coverage ✅ COMPLETE
- **`BollingerBandTracker`** — extended to analyze all tracked stocks via `StockSymbolRegistry`
  - New `analyzeAllStocks()` method iterates over registered stocks, excluding ETFs to avoid duplication with sector analysis
  - `trackAndAlert()` now combines sector + stock analyses into unified alerts
  - `buildSummaryReport()` shows separate "Sector ETFs" and "Stocks" sections
  - Added `StockSymbolRegistry` as constructor dependency
- **Tests**: 17 test cases covering sector analysis, stock analysis, ETF exclusion, combined alerts, and report formatting
- **All 665 tests passing** ✅

### Previous: Bollinger Band Analysis (March 26, 2026)
- `StatisticsUtil` — shared utility with `mean()`, `standardDeviation()`, `zScore()`, `percentileRank()`
- `BollingerSignalType` — enum: UPPER_BAND_TOUCH, LOWER_BAND_TOUCH, SQUEEZE
- `BollingerBandAnalysis` — record with %B, bandwidth, signals, interpretation methods
- `BollingerBandService` — 20-period SMA ± 2σ bands, %B calculation, bandwidth percentile, squeeze detection
- `BollingerBandTracker` — orchestrator wired into Scheduler for real-time and daily reporting
- Refactored `SectorRotationAnalyzer` and `TailRiskService` to use `StatisticsUtil`

### finmath-lib Evaluation ✅ DECIDED: NOT ADDING
- Library is a heavy academic framework (derivatives pricing, Monte Carlo, interest rate models)
- Our lightweight custom implementations are better suited for the tradebot's needs

## Architecture Decisions
- **Shared `StatisticsUtil`**: Eliminates statistical code duplication across services
- **No external quant library**: Custom implementations preferred over finmath-lib
- **`quant` package**: All quantitative analysis components live in `org.tradelite.quant`
- **ETF exclusion in stock analysis**: `analyzeAllStocks()` skips ETFs since they're already covered by `analyzeAllSectors()` via `SectorEtfRegistry`

## Next Steps
- Consider MACD indicator as next quant feature (uses same EMA concepts)
- Consider combining Bollinger + RS + ROC signals for multi-signal confirmation alerts
- Monitor API rate limits with tracked stocks + 20 ETFs across all tracking systems