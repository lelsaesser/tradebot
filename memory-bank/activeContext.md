# Active Context

## Current Work Focus

### Yahoo Finance Client + SQLite OHLCV Storage (April 12, 2026) ✅ COMPLETE
Implemented issue #245 — the foundational data layer for the VFI (Volume Flow Indicator) feature (#244). Introduces Yahoo Finance as a new data source for daily OHLCV data (open, high, low, close, adjusted close, volume), stored in a dedicated SQLite table.

**New Files:**
- `client/yahoo/dto/YahooOhlcvRecord.java` — Java record DTO: symbol, date, open, high, low, close, adjClose, volume
- `client/yahoo/YahooFinanceClient.java` — HTTP client using RestTemplate + Jackson `JsonNode` tree parsing for Yahoo Finance chart API (`/v8/finance/chart/{SYMBOL}?range=6mo&interval=1d`). User-Agent header, graceful HTTP 429 handling (log + return empty), metered via `ApiRequestMeteringService`
- `repository/YahooOhlcvRepository.java` — Interface: `saveAll(List<YahooOhlcvRecord>)`, `findBySymbol(String symbol, int days)`
- `repository/SqliteYahooOhlcvRepository.java` — SQLite implementation with auto-schema init, batch upsert (`INSERT OR REPLACE`) on `UNIQUE(symbol, date)`, date-based range queries
- `client/yahoo/YahooFinanceClientTest.java` — 12 tests: parsing, 429 handling, null body, malformed JSON, network errors, adjclose fallback
- `repository/SqliteYahooOhlcvRepositoryTest.java` — 15 tests: CRUD, upsert, date filtering, case sensitivity, large volumes, error paths

**Modified Files:**
- `service/ApiRequestMeteringService.java` — Added Yahoo counter (`incrementYahooRequests()`, `getYahooRequestCount()`, file persistence at `config/yahoo-monthly-requests.txt`, reset, summary)
- `service/ApiRequestMeteringServiceTest.java` — Updated all existing tests for 3-counter model, added Yahoo-specific test
- `.gitignore` — Added `config/yahoo-monthly-requests.txt`

**Design Decisions:**
- Date stored as `TEXT` (`YYYY-MM-DD`) not epoch seconds — simplifies queries for daily OHLCV data
- Yahoo response parsed with Jackson `JsonNode` tree walking (not DTOs) — Yahoo's nested JSON structure doesn't map cleanly to flat DTOs
- Volume stored as `INTEGER` / `long` — can exceed int max
- Adj close falls back to close if Yahoo doesn't provide it
- Timestamps converted to `America/New_York` timezone for date extraction
- No API key needed — Yahoo Finance chart API is public

### Partial EMA Calculation (April 11, 2026) ✅ COMPLETE
Changed `EmaService` to calculate every EMA for which enough data exists, instead of requiring all 200 data points. With fewer than 200 data points, shorter EMAs are still computed (e.g., 53 data points → EMA 9, 21, 50; EMA 100/200 = NaN).

**Changes:**
- `EmaAnalysis` — added `emasAvailable` field; EMA values are `Double.NaN` when insufficient data
- `EmaService` — `MIN_DATA_POINTS` changed from 200 to 9 (shortest EMA); each EMA calculated independently via `computeEmaOrNaN()`; new `countAvailable()` helper; `countEmasBelow()` skips NaN values
- `EmaSignalType.fromEmasBelow(int, int)` — now takes `emasAvailable` param; RED = below all *available* EMAs (not hardcoded 5)
- `EmaTracker.formatLine()` — shows `(X/N below)` where N is available EMAs
- `EmaServiceTest` — expanded from 7 to 17 tests (partial data: 10/25/53 points, NaN handling, helper methods)
- `EmaTrackerTest` — expanded from 6 to 7 tests (added partial EMA report format test)

### TelegramGateway Refactor in EmaTracker (April 11, 2026) ✅ COMPLETE
Refactored `EmaTracker` to inject the `TelegramGateway` interface instead of the concrete `TelegramClient`. This aligns with the existing profile-aware Telegram pattern where `TelegramClient` is the default production gateway and `LocalTelegramGateway` is active only in `dev`.

### EMA Daily Report Feature (April 4, 2026) ✅ COMPLETE
Added Exponential Moving Average (EMA) analysis with a once-per-day scheduled report that classifies stocks by their position relative to 5 EMAs (9, 21, 50, 100, 200 day).

**Classification:**
- 🟢 GREEN: Price above all 5 EMAs (strong uptrend)
- 🟡 YELLOW: Price below 2–4 EMAs (mixed/cautionary)
- 🔴 RED: Price below all 5 EMAs (downtrend)

**New Files:**
- `EmaSignalType` — enum: GREEN, YELLOW, RED
- `EmaAnalysis` — record: symbol, displayName, currentPrice, ema values (9/21/50/100/200), emasBelow count, signalType
- `EmaService` — calculates all 5 EMAs for a symbol using `StatisticsUtil.calculateEma()`, determines signal classification; returns `Optional<EmaAnalysis>`
- `EmaTracker` — orchestrates daily report: iterates all stock symbols, calls `EmaService.analyze()`, groups results by signal type (GREEN, then YELLOW, then RED), formats Telegram message, sends via `TelegramGateway`

**Modified Files:**
- `FeatureToggle` — added `EMA_REPORT` enum value
- `feature-toggles.json` — added `"emaReport": true`
- `Scheduler` — added `dailyEmaReport()` scheduled at 15:50 CET Mon–Fri; injected `EmaTracker`
- `SchedulerTest` — added `EmaTracker` mock + `dailyEmaReport_shouldSendReport` test

**Test Files:**
- `EmaServiceTest` — 7 tests: green/yellow/red classification, insufficient data, edge cases
- `EmaTrackerTest` — 6 tests: report formatting, no data, empty symbols
- `StatisticsUtilTest` — added `calculateEma` tests (basic, insufficient data, single value)

**Design Decisions:**
- EMA calculation uses standard formula: multiplier = 2/(period+1), seed = SMA of first N prices
- Reuses existing `PriceQuoteRepository` for historical daily prices (same data source as Bollinger/RSI)
- Report groups: GREEN → YELLOW → RED sections with summary counts
- Feature toggle gated via `FeatureToggle.EMA_REPORT` for easy enable/disable
- Scheduled at 15:50 CET (10 min after Bollinger report) to stagger API load

### Previous Completed Work (Summarized)
- **Market Holiday Detection Fix** (April 3) — uses Finnhub `previousClose` instead of SQLite stored price
- **PR #161 Review Follow-Up** (April 3) — default profile = production; `dev` opt-in only
- **Sector ROC Dead Zone Filter** (April 2) — ±0.25% dead zone filters noise near zero
- **DST-Aware Market Hours** (March 30) — all market-hours logic in `America/New_York` timezone

## Architecture Decisions
- **Separation of concerns**: `addPrice()` is purely a data collection method; RSI analysis happens separately in `analyzeAllSymbols()`
- **Delete-before-send pattern**: Hourly report messages are treated as updates — previous message is deleted before sending the new one
- **In-memory message ID tracking**: `lastTelegramReportMessageId` stored as instance field; resets on app restart
- **Display name registry**: `symbolDisplayNames` map populated during `addPrice()`, used by `analyzeAllSymbols()` for human-readable report names
- **Shared `StatisticsUtil`**: Eliminates statistical code duplication across services and now owns EMA / ROC / round-to-2-decimals helpers used by RS, momentum ROC, EMA, and dev seeding
- **Default profile = production**: production settings live in `application.yaml`; `dev` is opt-in and only used for local overrides / tooling
- **`quant` package**: All quantitative analysis components live in `org.tradelite.quant`
- **`TelegramGateway` interface**: All Telegram-sending components inject the `TelegramGateway` interface, allowing profile-based switching between `TelegramClient` (production) and `LocalTelegramGateway` (dev)

## Next Steps
- **VFI feature (PRD #244)**: Yahoo OHLCV data layer is complete. Remaining slices: VfiService calculation engine, CombinedSignalType enum, VfiTracker (combined RS+VFI hourly report), feature toggle, scheduler integration, DevDataSeeder extension, DevJobController endpoint
- Consider extending delete-before-send pattern to other recurring reports (tail risk, sector rotation)
- RSI + BB batched reporting pattern can be templated for future indicators
- Consider MACD indicator as next quant feature (uses same EMA concepts from StatisticsUtil)
- Consider combining Bollinger + RS + ROC + EMA signals for multi-signal confirmation alerts
- Monitor API rate limits with tracked stocks + 20 ETFs across all tracking systems + Yahoo Finance calls
