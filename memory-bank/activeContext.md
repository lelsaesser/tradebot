# Active Context

## Current Work Focus
Implemented batched RSI reporting following the same pattern as Bollinger Bands — RSI signals are now accumulated and sent as a consolidated hourly report instead of individual messages per stock. Previous report messages are deleted when new ones are sent.

## Recent Changes (March 29, 2026)

### RSI Batched Reporting ✅ COMPLETE
- **`RsiService`** — `addPrice()` no longer sends individual Telegram messages; signals accumulated in `pendingSignals` list
- New `sendRsiReport()` method builds consolidated report via `buildRsiReport()` and sends with `sendMessageAndReturnId()`
- Previous report messages deleted when new report sent (same delete-before-send pattern as BB)
- New `RsiSignal` record holds display name, RSI value, previous RSI, diff, and zone (OVERBOUGHT/OVERSOLD)
- Report format: header + grouped overbought/oversold sections + summary count
- **`Scheduler`** — renamed `hourlyBollingerBandMonitoring` → `hourlySignalMonitoring`; now runs both BB and RSI reports hourly
- **Tests**: 37 RsiServiceTest + 18 SchedulerTest all passing

### Telegram Delete-Before-Send for BB Reports ✅ COMPLETE
- **`TelegramClient`** — refactored `sendMessage` to use new `sendMessageAndReturnId(String)` which returns `OptionalLong` with the Telegram message ID; added `deleteMessage(long messageId)` using Telegram Bot API `deleteMessage` endpoint
- **New DTO**: `TelegramSendMessageResponse` — parses the `sendMessage` API response to extract `message_id` from the `result` object
- **`TelegramMessage`** — added `messageId` field (mapped from `message_id` via `@JsonProperty`)
- **`BollingerBandTracker`** — `sendDailyReport()` now calls `deletePreviousTelegramReport()` before sending; stores `lastTelegramReportMessageId` in-memory for next cycle
- **URL constants**: `BASE_URL` (sendMessage) and `DELETE_URL` (deleteMessage) extracted as package-visible constants in `TelegramClient`
- **Tests**: 9 TelegramClient tests + 18 BollingerBandTracker tests all passing

### Bollinger Band Alert Frequency Reduction ✅ COMPLETE
- **`Scheduler`** — removed `bollingerBandTracker::analyzeAndSendAlerts` from `stockMarketMonitoring()` (5-min loop)
- **New method**: `hourlyBollingerBandMonitoring()` with `@Scheduled(initialDelay = 0, fixedRate = 3600000)` — runs BB alerts once per hour during market open
- **Tests updated**: `stockMarketMonitoring_marketOpen_shouldRun` now expects 3 calls (was 4); added `hourlyBollingerBandMonitoring_marketOpen_shouldRun` and `hourlyBollingerBandMonitoring_marketClosed_shouldNotRun`

## Previous Changes (March 27, 2026)

### Bollinger Band Refinement ✅ COMPLETE
- **Split MIN_DATA_POINTS**: `MIN_DATA_POINTS = 20` for basic SMA/band calculation, `BANDWIDTH_HISTORY_MIN_DATA_POINTS = 40` for bandwidth percentile history
- **New signal**: `HISTORICAL_SQUEEZE` — bandwidth at historically low percentile (requires 40+ data points)
- **Absolute squeeze**: `SQUEEZE` signal fires when bandwidth ≤ 4% of SMA (works with just 20 points)
- **`BollingerBandAnalysis`** — new constants: `SQUEEZE_BANDWIDTH_THRESHOLD = 0.04`, `SQUEEZE_PERCENTILE_THRESHOLD = 10.0`; new methods: `hasBandwidthHistory()`, `isSqueeze()`, `isHistoricalSqueeze()`
- **`BollingerBandService`** — `detectSignals()` now takes `hasBandwidthHistory` flag; separate logic for absolute vs historical squeeze
- **`StatisticsUtil`** — added range-based `mean(List, start, end)`, `populationStdDev(List, start, end, mean)`, `percentile(List, value)`

### Bollinger Band Stock Coverage ✅ COMPLETE
- **`BollingerBandTracker`** — analyzes all tracked stocks via `StockSymbolRegistry` + sector ETFs
  - `analyzeAllStocks()` iterates registered stocks, excluding ETFs
  - `trackAndAlert()` combines sector + stock analyses into unified alerts
  - `buildSummaryReport()` shows separate "Sector ETFs" and "Stocks" sections
  - Added `StockSymbolRegistry` as constructor dependency

## Architecture Decisions
- **Delete-before-send pattern**: Hourly report messages are treated as updates — previous message is deleted before sending the new one, keeping the Telegram chat clean
- **In-memory message ID tracking**: `lastTelegramReportMessageId` stored as instance field; resets on app restart (acceptable since old messages eventually become stale)
- **Shared `StatisticsUtil`**: Eliminates statistical code duplication across services
- **No external quant library**: Custom implementations preferred over finmath-lib
- **`quant` package**: All quantitative analysis components live in `org.tradelite.quant`
- **ETF exclusion in stock analysis**: `analyzeAllStocks()` skips ETFs since they're covered by `analyzeAllSectors()`
- **Split data thresholds**: Basic Bollinger Bands work with just 20 data points; bandwidth percentile requires 40+

## Next Steps
- Consider extending delete-before-send pattern to other recurring reports (tail risk, sector rotation)
- RSI batched reporting now follows same pattern as BB — pattern can be templated for future indicators
- Consider MACD indicator as next quant feature (uses same EMA concepts)
- Consider combining Bollinger + RS + ROC signals for multi-signal confirmation alerts
- Monitor API rate limits with tracked stocks + 20 ETFs across all tracking systems