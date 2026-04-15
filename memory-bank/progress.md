# Progress Tracking

## Latest Milestone: Unified SymbolRegistry ✅ COMPLETE

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

### Data Persistence ✅
- SQLite: Finnhub price quotes, Twelve Data daily OHLCV (400 data points), momentum ROC state
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
- Total Tests: 923

## Future Enhancements

### Statistical (Open Issues)
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
