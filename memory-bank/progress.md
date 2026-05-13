# Progress Tracking

## Latest Milestone: Intraday Price Quotes for International Stocks (#382) — COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation (May 11, 2026)

#### Purpose
Enable real-time price monitoring for international stocks (German XETRA, Korean KRX) via Yahoo Finance's `meta.regularMarketPrice` field. Previously international stocks only had daily OHLCV data; now they get target price alerts, pullback buy alerts, and high-change (>5%) alerts.

#### New Components
- `LivePriceCache` — shared `@Service` bean (`ConcurrentHashMap<String, Double>`), replaces `FinnhubPriceEvaluator.lastPriceCache`
- `YahooPriceQuote` — record DTO (symbol, currentPrice, previousClose, dailyHigh, dailyLow, changePercent, timestamp)
- `YahooPriceEvaluator` — mirrors FinnhubPriceEvaluator: fetch, cache, persist, evaluate alerts for international symbols

#### Key Changes
- `YahooFinanceClient` — added `fetchCurrentPrice()` + `parseQuoteFromMeta()` (extracts `meta.regularMarketPrice` from chart endpoint)
- `MarketStatusService` — added `isExchangeOpen(symbol)`: `.DE` → XETRA 09:00–17:30 Europe/Berlin, `.KS` → KRX 09:00–15:30 Asia/Seoul
- `FinnhubPriceEvaluator` — refactored to use `LivePriceCache` (removed `lastPriceCache` field + `@Getter`)
- `PullbackBuyTracker` — injects `LivePriceCache` directly (no longer depends on FinnhubPriceEvaluator)
- `Scheduler` — `yahooPriceEvaluator.evaluatePrice()` runs every 5 min (outside US market-hours gate, handles its own exchange gating)
- `DevJobController` — `/dev/jobs/yahoo-price-evaluation` endpoint (18 total in run-all)
- `DevDataSeeder` — uses `LivePriceCache` instead of `FinnhubPriceEvaluator`

#### Design Decisions
- Yahoo quotes stored in existing `finnhub_price_quotes` table (no rename — avoids production migration)
- Per-exchange market hours (no international holiday detection — keep simple)
- `LivePriceCache` as `@Service` (not interface) — single concrete implementation, shared by all evaluators and consumers
- 3s delay between Yahoo requests (matching OhlcvFetcher pattern)
- Yahoo evaluator runs independently of US market hours

#### Tests: 1036 total, all passing, spotless clean

---

## Previous Milestone: API Request Metering SQLite Migration (#379) — COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation (May 11, 2026)

#### Purpose
Migrate `ApiRequestMeteringService` from per-call file I/O persistence to periodic SQLite flush. Eliminates ~100+ unnecessary file writes per 5-minute monitoring cycle while maintaining crash recovery via periodic persistence.

#### New Components
- `ApiMeteringRecord` — record (provider, month, count, lastUpdated) in `repository` package
- `ApiMeteringRepository` — interface with `saveAll(List)` + `findAll()`
- `SqliteApiMeteringRepository` — batch `INSERT OR REPLACE` via `BatchPreparedStatementSetter`

#### Key Changes
- `ApiRequestMeteringService` — complete refactor: removed file I/O, `ConcurrentHashMap<String, AtomicInteger>`, `flushCounters()`, `@PreDestroy` shutdown hook, stale-month detection on startup
- `Scheduler` — renamed `cleanupIgnoreSymbols()` → `periodicMaintenance()`, added `flushCounters()` call (every 10 min)
- `schema.sql` — added `api_request_metering` table
- `.gitignore` — removed 4 counter file entries
- `ApplicationTest` — removed obsolete `metering.counter-dir` property

#### Design Decisions
- 10-min flush interval (acceptable data loss for rate-limit awareness counters)
- Scheduler orchestrates flush (consolidates maintenance tasks)
- `resetCounters()` includes immediate flush (prevents race with periodic flush)
- Startup detects stale month → resets to 0 + warns (missed cron during downtime)
- Single row per provider (overwritten on reset, history lives in Telegram reports)

#### Tests: All passing, spotless clean

---

## Previous Milestone: Accumulation Detection Signal (#371) — COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation (May 9, 2026)

#### Purpose
Detect institutional accumulation by identifying stocks where price is weak (EMA9 < EMA21) but volume flow is bullish and accelerating (VFI > 0 AND VFI > signal line). This catches pre-trend institutional positioning before breakouts.

#### New Components
- `TrendDirection` — enum (RISING, FLAT, FALLING) in `quant` package
- `RsTrendResult` — record (rsValue, rsEma, rsTrend, rsEmaTrend) in `service` package
- `AccumulationSignal` — record with all alert formatting data
- `AccumulationDetectionService` — pure signal logic: evaluate(EmaAnalysis, VfiAnalysis, RsTrendResult)
- `AccumulationDetectionTracker` — orchestration: iterates stocks, calls services, sends consolidated Telegram alert

#### Key Changes
- `RelativeStrengthService` — added `getRsTrend(symbol)` with 5-day slope detection + ±0.5% dead zone
- `Scheduler` — added `0 0 10 * * MON-FRI` CET cron + `manualAccumulationDetection()`
- `PullbackBuyTracker` — added `log.warn()` when EMA/VFI analysis returns empty (for scan-logs.sh)
- `DevJobController` — `/dev/jobs/accumulation-detection` endpoint + run-all integration
- `FeatureToggle` — added `ACCUMULATION_DETECTION("accumulationDetection")`
- Bruno collection — added `accumulationDetection.yml`

#### Design Decisions
- No cooldown (fires daily for all qualifying stocks, add suppression if noisy)
- RS is informational context only (not a gating condition)
- Runs on all stocks (domestic + international)
- Service/tracker separation: service holds pure logic (testable without mocking), tracker orchestrates
- Warning logs surface persistent data gaps via scan-logs.sh

#### Tests: 1009 total, all passing

---

## Previous Milestone: Earnings Calendar Alerts (#363) — COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation (May 3, 2026)

#### Purpose
Daily Telegram report listing upcoming earnings for tracked stocks in the next 7 calendar days, using Finnhub's `/calendar/earnings` endpoint.

#### Key Changes
- New `EarningsCalendarResponse` DTO (maps Finnhub response with earningsCalendar array)
- New `EarningsCalendarTracker` — fetches 7-day window, filters against `symbolRegistry.getStocks()`, builds grouped-by-date message
- `FinnhubClient` — added `getEarningsCalendar(from, to)` following `getMarketHolidays()` pattern
- `Scheduler` — cron `0 15 8 * * MON-FRI` (CET) + `manualEarningsCalendarCheck()`
- `FeatureToggle` — added `EARNINGS_CALENDAR_ALERT("earningsCalendarAlert")`
- `DevJobController` — `/dev/jobs/earnings-calendar` endpoint + included in run-all (15 jobs total)
- Bruno collection — added `earningsCalendar.yml`

#### Design Decisions
- Condensed format: `• DisplayName (TICKER)` per line, grouped by date
- Stocks only (ETFs don't have earnings), silent when empty
- Single batch API call per day (~22/month), no per-symbol lookups
- No dedup table (daily report is idempotent — same events until they pass)
- No `/earnings` Telegram command (cron is sufficient)

#### Tests: 942+ total, all passing

---

## Previous Milestone: Market Holiday Detection Fix (#333) — COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation (May 3, 2026)

#### Purpose
Fix false positive "market holiday" detection that was skipping SQLite persistence for illiquid ETFs (e.g., REMX) where currentPrice == previousClose during normal trading.

#### Key Changes
- New `MarketHolidayService` — fetches Finnhub `/stock/market-holiday?exchange=US` at startup, caches holidays, retries on failure
- Handles full closures + early-close days (respects shortened hours like 09:30-13:00)
- `Scheduler` uses `marketHolidayService.isMarketOpen()` instead of `DateUtil.isStockMarketOpen()`
- `FinnhubPriceEvaluator`: removed per-symbol `isPotentialMarketHoliday()` heuristic, persistence guarded by service
- `DateUtil` unchanged (stays as simple static utility)

#### Tests: 951 total, all passing

---

## Previous Milestone: Target Prices + Stock Symbols SQLite Migration (#326) — COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation (May 3, 2026)

#### Purpose
Final PR in #320 JSON-to-SQLite migration. Migrate `config/target-prices-stocks.json`, `config/target-prices-coins.json`, and `config/stock-symbols.json` to SQLite tables. Eliminates synchronized file I/O and ObjectMapper usage from the core alerting and symbol registry systems.

#### Key Changes
- New `AssetType` enum, `TargetPriceRepository` + `StockSymbolRepository` (interface + JdbcTemplate impl)
- `TargetPriceProvider`: repository-backed, mutation API takes `AssetType` instead of file path
- `SymbolRegistry`: repository-backed with in-memory cache, reload from DB on mutation
- Telegram command processors (`Add`, `Remove`, `Set`) updated for `AssetType`
- `@PostConstruct` one-time migration from JSON (guard: table empty + file exists)
- DevDataSeeder seeds both new tables
- Follow-up cleanup: #359 (remove JSON files + migration code after deployment)

#### Tests: 945 total, all passing

---

## Previous Milestone: EMA Warm-up Fix + Price Cache Decoupling (#345, #332, #331) — COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation (April 30, 2026)

#### Purpose
Fix RS and VFI value discrepancies vs TradingView (insufficient EMA warm-up data) and decouple the Finnhub price cache from target price configuration so all tracked symbols get live price data.

#### Key Changes
- **#345**: RS lookback 80→400 days, VFI lookback 200→400 days, VFI numWindows dynamic (`Math.max(lastWindowEnd - LENGTH, SIGNAL_LENGTH + 1)`)
- **#332**: `FinnhubPriceEvaluator.evaluatePrice()` refactored — Loop 1 fetches/caches ALL symbols via `symbolRegistry.getAll()`, Loop 2 evaluates target prices from cache only
- **#331**: Verbose PullbackBuyTracker "no pullback pattern" log changed to DEBUG

#### Behavioral Changes
- All symbols (stocks + ETFs) get Finnhub prices cached every 5 min
- All symbols get persisted to SQLite (when `FINNHUB_PRICE_COLLECTION` enabled)
- High price change alerts (5%+) now fire for all symbols, not just target-price stocks
- PullbackBuyTracker can now analyze all stocks (no more "no cached price" skips)
- RS EMA now has ~230 recursive steps (vs 6), VFI signal EMA has ~150 windows (vs 1)

#### Tests: 932 total, all passing

---

## Previous Milestone: JdbcTemplate Migration (#314) — COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation (April 26, 2026)

#### Purpose
Migrate repository layer from raw JDBC (DataSource.getConnection / PreparedStatement / ResultSet) to Spring's JdbcTemplate. Eliminates ~76 instances of repetitive connection management boilerplate and prevents future technical debt. Evaluated in #305 — ORM (Hibernate/JPA and alternatives) not appropriate for this project.

#### Key Changes
- Added `spring-boot-starter-jdbc` + `spring-boot-starter-jdbc-test`
- Centralized DDL into `src/main/resources/schema.sql` (4 tables, 6 indexes)
- Created `DatabaseDirectoryInitializer` for DB parent dir creation
- DataSource auto-configured via `spring.datasource.*` with HikariCP (pool size 1)
- Removed manual `DataSource` bean from `BeanConfig`
- All 4 repositories rewritten to use `JdbcTemplate`
- Added `PriceQuoteRepository.saveAll()` + `PriceQuoteResponse.timestamp` field
- DevDataSeeder: routes inserts through `PriceQuoteRepository`, uses `JdbcTemplate`
- Repository tests: `@JdbcTest` slice tests (Spring Boot 4: `org.springframework.boot.jdbc.test.autoconfigure`)
- Dropped 12 error-path tests (tested framework behavior)
- Spring `DataAccessException` propagates naturally

#### Tests: ~915 total, all passing

#### Related Issues
- #305: ORM evaluation (closed — not appropriate for this project)
- #314: JdbcTemplate migration (this scope)

---

## Previous Milestone: EMA Pullback Buy Alert (#307) ✅ COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation (April 21, 2026)

#### Purpose
Real-time detection of "healthy pullback" buy opportunities. When a stock pulls back below EMA 9/21 but stays above EMA 50/100/200, and both RS vs SPY and VFI are positive, send a Telegram buy alert.

#### New Components
- `PullbackBuyTracker` — real-time pullback detector, runs every 5 min in `stockMarketMonitoring()`
- 22 unit tests

#### Key Changes
- `IgnoreReason` — per-reason TTL (`ttlSeconds` field), new `PULLBACK_BUY_ALERT` (8h)
- `TargetPriceProvider` — uses `reason.getTtlSeconds()` in `isSymbolIgnored()`
- `FeatureToggle` — added `PULLBACK_BUY_ALERT`
- `Scheduler` — pullback tracker wired after `evaluatePrice()`, manual method
- `DevJobController` — new endpoint + smoke test entry (14 jobs total)
- `DevDataSeeder` — seeds OHLCV for all bundle symbols, seeds price cache, crafts pullback price using OHLCV-based EMAs, injected `FinnhubPriceEvaluator`
- Bruno collection — added `pullbackBuyAlert.yml`

#### Design Decisions
- No separate Finnhub API calls — reuses `FinnhubPriceEvaluator.lastPriceCache`
- Strict conditions — ALL of EMA 50/100/200 must be above price
- 8h cooldown (once per trading day, re-alert next morning)
- One message per stock, no emoji
- Per-reason TTL in `IgnoreReason` enum

#### Tests: 926 total, all passing

#### Related Issues
- #307: EMA pullback buy alert (this scope)
- #308: Future extension — sector trend filtering (requires sector membership mapping)

---

## Previous Milestone: Unified SymbolRegistry ✅ COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation Complete (April 15, 2026)

#### Purpose
Replaced the split `SectorEtfRegistry` (static class) + `StockSymbolRegistry` (`@Service`) with a single unified `SymbolRegistry` `@Service` bean. Eliminates duplicate ETF knowledge, confusing dual API, and two-pass iteration patterns.

#### New Component
- `SymbolRegistry` — single source of truth for all tracked symbols. ETF definitions as hardcoded constants, stocks from JSON with runtime add/remove. Full API: `getAll()`, `getAllEtfs()`, `getBroadSectorEtfs()`, `getThematicEtfs()`, `getStocks()`, `isEtf()`, `isSectorEtf()`, `fromString()`, `addSymbol()`, `removeSymbol()`

#### Deleted Components
- `SectorEtfRegistry` — replaced by SymbolRegistry
- `StockSymbolRegistry` — replaced by SymbolRegistry

#### Migration Scope
- ~18 main source files migrated
- ~17 test files migrated
- Zero remaining references to old classes

#### Tests: 923 total, all passing

---

## Previous Milestone: OHLCV 400 Data Points + DailyPriceProvider ✅ COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation Complete (April 15, 2026)

#### Purpose
Increase OHLCV backfill from 136 to 400 data points (supports 200-day EMA), extend OHLCV fetch to include ETFs, create unified daily price access layer.

#### New Components
- `DailyPriceProvider` — OHLCV-first, Finnhub-fallback. Same `findDailyClosingPrices()` API.

#### Changes
- `OhlcvFetcher` — backfill 400, lookback 600, includes all ETFs
- EmaService, BollingerBandService, RelativeStrengthService, MomentumRocService — migrated to DailyPriceProvider
- DevDataSeeder — OHLCV seeding increased to 400 days
- TailRiskService unchanged (uses `findDailyChangePercents`, not daily closing prices)

#### Tests: 890 total at time of merge

---

## Previous Milestone: VFI Combined RS+VFI Daily Report ✅ COMPLETE

**Status**: ✅ **PRODUCTION READY**

### Implementation Complete (April 14, 2026)

#### Purpose
PRD #244 core deliverable: Volume Flow Indicator combined with Relative Strength into a traffic-light daily report.

#### Components
- `VfiService` — VFI calculation from OHLCV data (130-day lookback, 5-period signal EMA)
- `VfiAnalysis` — record with `isVfiPositive()` (both VFI > 0 AND signal > 0)
- `CombinedSignalType` — GREEN/YELLOW/RED classification enum
- `VfiTracker` — daily report orchestrator at 9:00 CET
- Feature toggle: `VFI_REPORT` in `FeatureToggle` enum
- Dev tooling: `POST /dev/jobs/vfi-report`, DevDataSeeder OHLCV seeding

#### Design Decision
Daily at 9:00 CET (not hourly) because VFI uses daily OHLCV bars. #258 can upgrade to hourly when intraday OHLCV is available.

---

## PRD #244 — Volume Flow Indicator: COMPLETE ✅

All sub-issues closed:
- #245 Yahoo Finance Client + SQLite OHLCV Storage
- #246 Yahoo OHLCV Historical Backfill + Fetch Orchestration
- #247 VFI Calculation Engine (VfiService + VfiAnalysis + CombinedSignalType)
- #248 Combined RS+VFI Daily Report + Scheduler Integration
- #249 VFI Dev Tooling (Seeder + Manual Trigger Endpoint)
- #251 Replace Yahoo Finance with Twelve Data API
- #253 Twelve Data Client + API Configuration + Metering
- #254 Wire TwelveDataClient into OhlcvFetcher
- #255 OHLCV Fetch Orchestration with Twelve Data

Follow-up issues (open):
- #258 Intraday OHLCV + intraday VFI zero-line cross detection
- #262 Increase OHLCV to 400 data points + DailyPriceProvider (COMPLETE)
- #265 Setup Twelve Data API key in production
- #267 Unified SymbolRegistry (COMPLETE)

---

## Seven-Pronged Statistical Analysis

| Approach | Component | Signal | Schedule |
|----------|-----------|--------|----------|
| **Z-Score Analysis** | `SectorRotationAnalyzer` | Industry performance anomalies | Daily (after market) |
| **Relative Strength vs SPY** | `SectorRelativeStrengthTracker` | RS EMA crossovers | Real-time (5 min) |
| **Momentum ROC** | `SectorMomentumRocTracker` | Zero-line crossovers | Real-time (5 min) |
| **Tail Risk (Kurtosis + Skewness)** | `TailRiskTracker` | Fat tail + directional bias | Daily 13:00 CET |
| **Bollinger Bands** | `BollingerBandTracker` | Band touch + squeeze detection | Hourly + Daily 15:40 CET |
| **EMA Classification** | `EmaTracker` | Price vs 5 EMAs (green/yellow/red) | Daily 15:50 CET |
| **VFI + RS Combined** | `VfiTracker` | Volume flow + RS confirmation | Daily 09:00 CET |
| **EMA Pullback Buy** | `PullbackBuyTracker` | Pullback into 21-50 EMA zone + RS↑ + VFI↑ | Real-time (5 min) |
| **Yahoo Intraday Price** | `YahooPriceEvaluator` | Target price + high-change alerts for intl stocks | Real-time (5 min) |
| **Earnings Calendar** | `EarningsCalendarTracker` | Upcoming earnings in 7-day window | Daily 08:15 CET |
| **Accumulation Detection** | `AccumulationDetectionTracker` | EMA9 < EMA21 + VFI↑ (pre-breakout positioning) | Daily 10:00 CET |

---

## Completed Features Summary

### Core Bot Functionality ✅
- Telegram bot integration with command processing
- Price monitoring for stocks (Finnhub) and cryptocurrencies (CoinGecko)
- Target price alerts, RSI calculation and monitoring
- Insider transaction tracking, Sector rotation tracking with Z-Score
- Relative Strength vs SPY benchmark
- SQLite historical price persistence, Feature toggle system
- Daily sector RS summary with streak tracking
- Real-time sector RS crossover alerts, Momentum ROC sector tracking
- Tail Risk (Kurtosis + Skewness) analysis
- Bollinger Band analysis (sectors + stocks, squeeze detection)
- Telegram delete-before-send for recurring hourly reports
- RSI batched reporting (consolidated hourly)
- EMA daily report (partial EMAs, green/yellow/red classification)
- **VFI + RS combined daily report** (traffic-light classification)
- **DailyPriceProvider** (OHLCV-first, Finnhub-fallback)
- **Unified SymbolRegistry** (single source of truth for all symbols)
- **EMA Pullback Buy Alerts** (real-time pullback detection with RS+VFI confirmation)
- **Earnings Calendar Alerts** (daily 7-day look-ahead report via Finnhub)
- **Intraday price monitoring for international stocks** (Yahoo Finance meta.regularMarketPrice, LivePriceCache)

### Dev Tooling & Smoke Test ✅
- Bruno API collection (`TradeliteBrunoCollection/DevController/`) with 18 endpoint requests
- DevJobController with 18 individual endpoints + phased `run-all` composite endpoint (18 jobs)
- Pre-deployment smoke test script (`scripts/run-smoke-test.sh`) — validates all jobs in 4 phases
- DevDataSeeder for synthetic dev data (400 days OHLCV, price quotes, RSI, RS, ROC)

### Data Persistence ✅
- SQLite via JdbcTemplate: Finnhub price quotes, Twelve Data daily OHLCV (400 data points), Yahoo Finance international OHLCV, momentum ROC state, ignored symbols, API request metering (periodic flush). Schema centralized in `schema.sql`.
- JSON: target prices, sector performance, insider transactions, RS streaks, RSI data, feature toggles, stock symbols
- API metering: Finnhub, CoinGecko, Twelve Data, Yahoo Finance (in-memory AtomicInteger counters, SQLite persistence every 10 min)

### External Data Sources ✅
- Finnhub (stock prices, insider transactions, market holidays, earnings calendar)
- CoinGecko (crypto prices)
- Twelve Data (daily OHLCV — 400 data points, 8 req/min)
- Yahoo Finance (international stock OHLCV + intraday price quotes via ProcessBuilder + curl)
- FinViz (sector performance web scraping)
- Telegram (bot messaging)

### Telegram Commands ✅
- `/set buy/sell <symbol> <price>`, `/show stocks/coins/all`, `/rsi <symbol>`, `/add <TICKER> <Name>`, `/remove <TICKER>`

## Test Coverage Status
- Target: 97% line coverage
- Current: 97%
- Total Tests: ~1036

## Future Enhancements

### Statistical (Open Issues)
- **#308**: Extend pullback buy alert with sector trend filtering (requires sector membership mapping)
- **#258**: Intraday OHLCV + intraday VFI (would enable hourly VFI — price quotes done via #382, full OHLCV bars remain)
- **#265**: Twelve Data API key in production
- **#257**: Bug: ETF symbols can be added to stock symbols list
- MACD indicator (uses same EMA concepts)
- Multi-signal confirmation alerts
- VIX integration, Sharpe/Sortino ratios

### Infrastructure (Future)
- Migrate existing JSON persistence to SQLite
- Data retention/cleanup policies
- Dashboard UI (#233-#243)
