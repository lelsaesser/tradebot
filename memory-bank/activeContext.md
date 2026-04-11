# Active Context

## Current Work Focus

### EMA Daily Report Feature (April 4, 2026) ✅ COMPLETE
Added Exponential Moving Average (EMA) analysis with a once-per-day scheduled report that classifies stocks by their position relative to 5 EMAs (9, 21, 50, 100, 200 day).

**Classification:**
- 🟢 GREEN: Price above all 5 EMAs (strong uptrend)
- 🟡 YELLOW: Price below 2–4 EMAs (mixed/cautionary)
- 🔴 RED: Price below all 5 EMAs (downtrend)

**New Files:**
- `EmaSignalType` — enum: GREEN, YELLOW, RED
- `EmaAnalysis` — record: symbol, displayName, currentPrice, ema values (9/21/50/100/200), emasAbove count, emasBelow count, signalType
- `EmaService` — calculates all 5 EMAs for a symbol using `StatisticsUtil.calculateEma()`, determines signal classification; returns `Optional<EmaAnalysis>`
- `EmaTracker` — orchestrates daily report: iterates all stock symbols, calls `EmaService.analyze()`, groups results by signal type (RED first, then YELLOW, then GREEN), formats Telegram message, sends via `TelegramClient`; gated by `FeatureToggle.EMA_REPORT`

**Modified Files:**
- `StatisticsUtil` — added `calculateEma(List<Double>, int)` static method for EMA calculation (reused by `EmaService`)
- `FeatureToggle` — added `EMA_REPORT` enum value
- `feature-toggles.json` — added `"EMA_REPORT": true`
- `Scheduler` — added `dailyEmaReport()` scheduled at 15:50 CET Mon–Fri; injected `EmaTracker`
- `SchedulerTest` — added `EmaTracker` mock + `dailyEmaReport_shouldSendReport` test

**Test Files:**
- `EmaServiceTest` — 7 tests: green/yellow/red classification, insufficient data, edge cases
- `EmaTrackerTest` — 6 tests: report formatting, feature toggle disabled, no data, empty symbols
- `StatisticsUtilTest` — added `calculateEma` tests (basic, insufficient data, single value)

**Design Decisions:**
- EMA calculation uses standard formula: multiplier = 2/(period+1), seed = SMA of first N prices
- Reuses existing `PriceQuoteRepository` for historical daily prices (same data source as Bollinger/RSI)
- Report sorted: RED → YELLOW → GREEN (worst first for quick scanning)
- Feature toggle gated for easy enable/disable
- Scheduled at 15:50 CET (10 min after Bollinger report) to stagger API load

### Market Holiday Detection Fix (April 3, 2026) ✅ COMPLETE
Fixed market holiday detection that was broken due to timing gap between last price fetch and actual market close.

**Root Cause:** The scheduler runs on a `fixedRate` interval (every 5 min), not aligned to clock boundaries. `MARKET_CLOSE = LocalTime.of(16, 0)` NY time meant the last fetch before close was typically at ~15:57, and the next fetch at ~16:02 was already skipped (market closed). This meant our last recorded price for the day was the 15:57 quote — NOT the actual closing price. The holiday detection compared this last recorded price against the next day's current price (Finnhub returns `previousClose` as current price when market is closed). Since the 15:57 price ≠ actual close price, the comparison almost never matched, so holidays were never detected.

**Solution:** Changed `isPotentialMarketHoliday()` to use Finnhub's `previousClose` field (`pc`) from the `PriceQuoteResponse` instead of comparing against our last SQLite-stored price from the previous day.

- `previousClose` is the official closing price from the exchange, always accurate
- When market is closed (holiday), Finnhub returns `currentPrice == previousClose`
- Comparing `currentPrice == previousClose` reliably detects holidays regardless of fetch timing

**Changes:**
- `FinnhubPriceEvaluator.isPotentialMarketHoliday(String, double, double)` — now takes `previousClose` as 3rd param; compares `currentPrice == previousClose` directly instead of looking up last stored price from SQLite
- `FinnhubPriceEvaluator.evaluatePrice()` — passes `previousClose` from `PriceQuoteResponse` to `isPotentialMarketHoliday()`
- `RsiService.addPrice(TickerSymbol, double, double, LocalDate)` — new `previousClose` param, forwarded to `isPotentialMarketHoliday()`
- `RsiPriceFetcher.fetchStockClosingPrices()` — passes `quote.getPreviousClose()` to `rsiService.addPrice()`
- `RsiPriceFetcher.fetchCryptoClosingPrices()` — passes `0.0` for `previousClose` (crypto has no holiday detection)
- All tests updated for new signatures; 739 total tests pass

### PR #161 Review Follow-Up (April 3, 2026) ✅ COMPLETE
Aligned the dev-environment branch with the remaining PR review feedback.

**Key outcomes:**
- Default Spring behavior is now production-like via `application.yaml`; `application-prod.yaml` was removed
- `dev` is the only opt-in local profile and owns local Telegram mocking, scheduler disablement, dev DB isolation, and analytics seeding
- `LocalTelegramGateway` is active only in `dev`; the real `TelegramClient` is the default gateway outside `dev`
- `DevDataSeeder` constructor wiring is explicit, avoiding Spring constructor ambiguity
- Shared EMA / ROC / rounding helpers now live in `StatisticsUtil`
- Dev manual job endpoints now return real HTTP success/failure based on job execution status

**Verification:**
- `mvn -q -DskipTests test-compile` passes
- full `mvn -q test` pending after final doc updates in this pass

### Sector ROC Dead Zone Filter (April 2, 2026) ✅ COMPLETE
Fixed false alerts in sector momentum ROC analysis. ROC₁₀ values oscillating near zero caused rapid positive/negative crossover alerts.

### DST-Aware Market Hours (March 30, 2026) ✅ COMPLETE
Fixed `isStockMarketOpen` and `isMarketOffHours` to automatically handle US/Europe DST transitions.

## Architecture Decisions
- **Separation of concerns**: `addPrice()` is purely a data collection method; RSI analysis happens separately in `analyzeAllSymbols()`
- **Delete-before-send pattern**: Hourly report messages are treated as updates — previous message is deleted before sending the new one
- **In-memory message ID tracking**: `lastTelegramReportMessageId` stored as instance field; resets on app restart
- **Display name registry**: `symbolDisplayNames` map populated during `addPrice()`, used by `analyzeAllSymbols()` for human-readable report names
- **Shared `StatisticsUtil`**: Eliminates statistical code duplication across services and now owns EMA / ROC / round-to-2-decimals helpers used by RS, momentum ROC, and dev seeding
- **Default profile = production**: production settings live in `application.yaml`; `dev` is opt-in and only used for local overrides / tooling
- **`quant` package**: All quantitative analysis components live in `org.tradelite.quant`

## Next Steps
- Consider extending delete-before-send pattern to other recurring reports (tail risk, sector rotation)
- RSI + BB batched reporting pattern can be templated for future indicators
- Consider MACD indicator as next quant feature (uses same EMA concepts from StatisticsUtil)
- Consider combining Bollinger + RS + ROC + EMA signals for multi-signal confirmation alerts
- Monitor API rate limits with tracked stocks + 20 ETFs across all tracking systems
