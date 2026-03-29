# Active Context

## Current Work Focus
Refined RSI batched reporting — separated concerns so `addPrice()` only stores data, and RSI calculation + signal detection happens in `analyzeAllSymbols()` called by `sendRsiReport()`. This follows the same consolidated hourly report pattern as Bollinger Bands.

## Recent Changes (March 29, 2026)

### RSI Reporting Refinement ✅ COMPLETE
- **`RsiService`** — removed `pendingSignals` accumulator; `addPrice()` now purely stores price data and display names without calculating RSI
- New `analyzeAllSymbols()` method iterates all stored price history, calculates RSI for each symbol with sufficient data (≥15 prices), and returns `List<RsiSignal>` for overbought (≥70) / oversold (≤30)
- `sendRsiReport()` calls `analyzeAllSymbols()`, builds consolidated report, sends via `sendMessageAndReturnId()`, and deletes previous report message
- `symbolDisplayNames` map tracks display names registered during `addPrice()` calls (used by `analyzeAllSymbols()` for report formatting)
- **`RsiPriceFetcher`** — `addPrice()` no longer triggers RSI calculation, only stores prices
- **Tests**: 46 RsiServiceTest + 709 total tests all passing

### RSI Batched Reporting (Initial) ✅ COMPLETE
- **`Scheduler`** — `hourlySignalMonitoring()` runs both BB and RSI reports hourly
- `sendRsiReport()` builds consolidated report via `buildRsiReport()` with grouped overbought/oversold sections
- `RsiSignal` record holds display name, RSI value, previous RSI, diff, and zone
- Delete-before-send pattern: previous report message deleted when new report sent

### Telegram Delete-Before-Send for BB Reports ✅ COMPLETE
- **`TelegramClient`** — `sendMessageAndReturnId(String)` returns `OptionalLong` with Telegram message ID; `deleteMessage(long messageId)` calls Telegram Bot API
- **`TelegramSendMessageResponse`** DTO parses `message_id` from API response
- **`BollingerBandTracker`** — `sendDailyReport()` deletes previous message before sending new one

### Bollinger Band Alert Frequency Reduction ✅ COMPLETE
- **`Scheduler`** — BB alerts run once per hour via `hourlySignalMonitoring()` (renamed from `hourlyBollingerBandMonitoring`)

## Architecture Decisions
- **Separation of concerns**: `addPrice()` is purely a data collection method; RSI analysis happens separately in `analyzeAllSymbols()`
- **Delete-before-send pattern**: Hourly report messages are treated as updates — previous message is deleted before sending the new one
- **In-memory message ID tracking**: `lastTelegramReportMessageId` stored as instance field; resets on app restart
- **Display name registry**: `symbolDisplayNames` map populated during `addPrice()`, used by `analyzeAllSymbols()` for human-readable report names
- **Shared `StatisticsUtil`**: Eliminates statistical code duplication across services
- **`quant` package**: All quantitative analysis components live in `org.tradelite.quant`

## Next Steps
- Consider extending delete-before-send pattern to other recurring reports (tail risk, sector rotation)
- RSI + BB batched reporting pattern can be templated for future indicators
- Consider MACD indicator as next quant feature (uses same EMA concepts)
- Consider combining Bollinger + RS + ROC signals for multi-signal confirmation alerts
- Monitor API rate limits with tracked stocks + 20 ETFs across all tracking systems