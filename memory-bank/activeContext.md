# Active Context

## Current Work Focus

### Case-Insensitive Subcommands (Issue #144) ✅ COMPLETEa
Made all Telegram command subcommands case insensitive. Previously `/show ALL` or `/set BUY AAPL 150` would fail; now they work correctly.

**Changes:**
- `TelegramMessageProcessor.parseCommand()` — lowercases subcommand for `/show` and direction for `/set` at parse time
- `ShowCommandProcessor` — `isValidCommand()` and `processCommand()` use `equalsIgnoreCase()` for defense-in-depth
- `SetCommandProcessor` — `isValidSetCommand()` uses `equalsIgnoreCase()` for buy/sell direction
- Tests added: 7 new tests across `TelegramMessageProcessorTest`, `ShowCommandProcessorTest`, `SetCommandProcessorTest`
- All 164 tests pass

### Previous: RSI Current Price from Cache
Enhanced RSI analysis to use current price from cache during `analyzeAllSymbols()`, allowing RSI calculation with 14 historical daily prices + 1 live intraday price. This mirrors how Bollinger Bands use live prices.

## Recent Changes (March 29, 2026)

### RSI Current Price from Cache ✅ COMPLETE
- **`RsiService.analyzeAllSymbols()`** — now appends current live price from evaluator caches (Finnhub/CoinGecko) to historical closing prices before RSI calculation
- New `getCurrentPriceFromCacheByKey(String symbolKey)` helper method looks up current price by symbol key across both Finnhub (stocks) and CoinGecko (crypto) caches
- With 14 historical prices + 1 cached live price = 15 total, RSI becomes calculable even before today's close is recorded
- Early-exit threshold lowered from `< RSI_PERIOD + 1` (15) to `< RSI_PERIOD` (14) to allow cache-supplemented calculation
- **Tests**: 50 RsiServiceTest + 713 total tests all passing
- New tests: `testGetCurrentPriceFromCacheByKey_stock/crypto/notFound`, `testAnalyzeAllSymbols_usesCurrentPriceFromCache`

### RSI Reporting Refinement ✅ COMPLETE
- **`RsiService`** — removed `pendingSignals` accumulator; `addPrice()` now purely stores price data and display names without calculating RSI
- `analyzeAllSymbols()` iterates all stored price history, calculates RSI for each symbol with sufficient data, and returns `List<RsiSignal>` for overbought (≥70) / oversold (≤30)
- `sendRsiReport()` calls `analyzeAllSymbols()`, builds consolidated report, sends via `sendMessageAndReturnId()`, and deletes previous report message
- `symbolDisplayNames` map tracks display names registered during `addPrice()` calls (used by `analyzeAllSymbols()` for report formatting)
- **`RsiPriceFetcher`** — `addPrice()` no longer triggers RSI calculation, only stores prices

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
