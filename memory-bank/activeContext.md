# Active Context

## Current Work Focus
Refined Bollinger Band analysis with split data point thresholds — 20 points for basic band calculation, 40 points for bandwidth percentile history. Added `HISTORICAL_SQUEEZE` signal type and absolute bandwidth squeeze detection. All 677 tests passing.

## Recent Changes (March 27, 2026)

### Bollinger Band Refinement ✅ COMPLETE
- **Split MIN_DATA_POINTS**: `MIN_DATA_POINTS = 20` for basic SMA/band calculation, `BANDWIDTH_HISTORY_MIN_DATA_POINTS = 40` for bandwidth percentile history
- **New signal**: `HISTORICAL_SQUEEZE` — bandwidth at historically low percentile (requires 40+ data points)
- **Absolute squeeze**: `SQUEEZE` signal fires when bandwidth ≤ 4% of SMA (works with just 20 points)
- **`BollingerBandAnalysis`** — new constants: `SQUEEZE_BANDWIDTH_THRESHOLD = 0.04`, `SQUEEZE_PERCENTILE_THRESHOLD = 10.0`; new methods: `hasBandwidthHistory()`, `isSqueeze()`, `isHistoricalSqueeze()`
- **`BollingerBandService`** — `detectSignals()` now takes `hasBandwidthHistory` flag; separate logic for absolute vs historical squeeze
- **`StatisticsUtil`** — added range-based `mean(List, start, end)`, `populationStdDev(List, start, end, mean)`, `percentile(List, value)`
- **All 677 tests passing** ✅

### Bollinger Band Stock Coverage ✅ COMPLETE
- **`BollingerBandTracker`** — analyzes all tracked stocks via `StockSymbolRegistry` + sector ETFs
  - `analyzeAllStocks()` iterates registered stocks, excluding ETFs
  - `trackAndAlert()` combines sector + stock analyses into unified alerts
  - `buildSummaryReport()` shows separate "Sector ETFs" and "Stocks" sections
  - Added `StockSymbolRegistry` as constructor dependency

### finmath-lib Evaluation ✅ DECIDED: NOT ADDING
- Library is a heavy academic framework (derivatives pricing, Monte Carlo, interest rate models)
- Our lightweight custom implementations are better suited for the tradebot's needs

## Architecture Decisions
- **Shared `StatisticsUtil`**: Eliminates statistical code duplication across services
- **No external quant library**: Custom implementations preferred over finmath-lib
- **`quant` package**: All quantitative analysis components live in `org.tradelite.quant`
- **ETF exclusion in stock analysis**: `analyzeAllStocks()` skips ETFs since they're covered by `analyzeAllSectors()`
- **Split data thresholds**: Basic Bollinger Bands work with just 20 data points (newly tracked stocks can get signals sooner); bandwidth percentile requires 40+ for reliable history

## Next Steps
- Consider MACD indicator as next quant feature (uses same EMA concepts)
- Consider combining Bollinger + RS + ROC signals for multi-signal confirmation alerts
- Monitor API rate limits with tracked stocks + 20 ETFs across all tracking systems