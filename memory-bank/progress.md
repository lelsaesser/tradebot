# Progress Tracking

## Latest Milestone: Target Prices + Stock Symbols SQLite Migration (#326) — COMPLETE

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

### Dev Tooling & Smoke Test ✅
- Bruno API collection (`TradeliteBrunoCollection/DevController/`) with 15 endpoint requests
- DevJobController with 15 individual endpoints + phased `run-all` composite endpoint (14 jobs)
- Pre-deployment smoke test script (`scripts/run-smoke-test.sh`) — validates all 13 jobs in 4 phases
- DevDataSeeder for synthetic dev data (400 days OHLCV, price quotes, RSI, RS, ROC)

### Data Persistence ✅
- SQLite via JdbcTemplate: Finnhub price quotes, Twelve Data daily OHLCV (400 data points), momentum ROC state, ignored symbols. Schema centralized in `schema.sql`.
- JSON: target prices, sector performance, insider transactions, RS streaks, RSI data, feature toggles, stock symbols
- API metering: Finnhub, CoinGecko, Twelve Data

### External Data Sources ✅
- Finnhub (stock prices, insider transactions)
- CoinGecko (crypto prices)
- Twelve Data (daily OHLCV — 400 data points, 8 req/min)
- FinViz (sector performance web scraping)
- Telegram (bot messaging)

### Telegram Commands ✅
- `/set buy/sell <symbol> <price>`, `/show stocks/coins/all`, `/rsi <symbol>`, `/add <TICKER> <Name>`, `/remove <TICKER>`

## Test Coverage Status
- Target: 97% line coverage
- Current: 97%
- Total Tests: ~945

## Future Enhancements

### Statistical (Open Issues)
- **#308**: Extend pullback buy alert with sector trend filtering (requires sector membership mapping)
- **#258**: Intraday OHLCV + intraday VFI (would enable hourly VFI)
- **#265**: Twelve Data API key in production
- **#257**: Bug: ETF symbols can be added to stock symbols list
- MACD indicator (uses same EMA concepts)
- Multi-signal confirmation alerts
- VIX integration, Sharpe/Sortino ratios

### Infrastructure (Future)
- Migrate existing JSON persistence to SQLite
- Data retention/cleanup policies
- Dashboard UI (#233-#243)
