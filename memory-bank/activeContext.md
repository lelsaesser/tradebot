# Active Context

## Current Work Focus

### Unified SymbolRegistry Refactoring (April 15, 2026) ✅ COMPLETE
Implemented issue #267 — replaced the split `SectorEtfRegistry` (static) + `StockSymbolRegistry` (service) with a single unified `SymbolRegistry` `@Service` bean.

**New File:**
- `common/SymbolRegistry.java` — single source of truth for all tracked symbols (ETFs + stocks). Constants for ETF definitions (broad sector, thematic, benchmark). JSON-loaded stocks with add/remove. Full API: `getAll()`, `getAllEtfs()`, `getBroadSectorEtfs()`, `getThematicEtfs()`, `getStocks()`, `isEtf()`, `isSectorEtf()`, `fromString()`, `addSymbol()`, `removeSymbol()`

**Deleted Files:**
- `common/SectorEtfRegistry.java` — replaced by SymbolRegistry
- `service/StockSymbolRegistry.java` — replaced by SymbolRegistry

**Migrated:** ~18 main files + ~17 test files. 923 tests passing.

### OHLCV 400 Data Points + DailyPriceProvider (April 15, 2026) ✅ COMPLETE
Implemented issue #262 — increased OHLCV backfill from 136 to 400 data points, added ETFs to the OHLCV fetch, and created `DailyPriceProvider` to unify price data access.

**New Files:**
- `service/DailyPriceProvider.java` — tries OHLCV (Twelve Data) first, falls back to Finnhub. Same `findDailyClosingPrices()` signature as PriceQuoteRepository.
- `service/DailyPriceProviderTest.java` — 4 tests

**Modified Files:**
- `OhlcvFetcher` — backfill 400, lookback 600, includes all ETFs via `SymbolRegistry.getAll()`
- `EmaService`, `BollingerBandService`, `RelativeStrengthService`, `MomentumRocService` — migrated from PriceQuoteRepository to DailyPriceProvider
- `DevDataSeeder` — OHLCV seeding increased to 400 days

### VFI Combined RS+VFI Daily Report (April 14, 2026) ✅ COMPLETE
Implemented issues #248 + #249 from PRD #244.

**New Files:**
- `quant/VfiTracker.java` — orchestrates daily RS+VFI combined report with GREEN/YELLOW/RED traffic-light classification
- `quant/VfiTrackerTest.java` — 9 tests

**Modified Files:**
- `FeatureToggle.java` — added `VFI_REPORT`
- `Scheduler.java` — added `dailyVfiReport()` at 9:00 CET + `manualVfiReport()`
- `DevJobController.java` — added `POST /dev/jobs/vfi-report`
- `DevDataSeeder.java` — added OhlcvRepository dependency, seeds synthetic OHLCV data

**Design Decision:** VFI report runs daily at 9:00 CET (pre-market), not hourly, because VFI uses daily OHLCV bars that don't change intraday. Issue #258 can upgrade to hourly when intraday OHLCV is available.

### Pre-Deployment Smoke Test Infrastructure (April 2026) ✅ COMPLETE
Implemented issue #293 — phased `run-all` dev endpoint and smoke test script for pre-deployment validation.

**Components:**
- `DevJobController.runAll()` — 4-phase execution: seed → OHLCV fetch (3 symbols) → 10 parallel jobs → VFI report
- `scripts/run-smoke-test.sh` — bash script that curls `run-all` and validates JSON response
- Bruno API collection (`TradeliteBrunoCollection/DevController/`) — 14 individual + 1 run-all request for manual testing via Bruno API client

### Previous Completed Work (Summarized)
- **Yahoo Finance Client + SQLite OHLCV Storage** (#245) — foundational data layer
- **VFI Calculation Engine** (#247) — VfiService, VfiAnalysis, CombinedSignalType
- **Twelve Data API replacement** (#251) — replaced Yahoo Finance with Twelve Data
- **OHLCV Fetch Orchestration** (#255) — TwelveDataClient + OhlcvFetcher
- **Partial EMA Calculation** — EMA calculates available periods (min 9 data points)
- **EMA Daily Report** — green/yellow/red classification
- **Market Holiday Detection Fix** — uses Finnhub `previousClose`
- **DST-Aware Market Hours** — all in `America/New_York`

## Architecture Decisions
- **Unified SymbolRegistry**: Single `@Service` bean owns all symbol knowledge (ETFs as hardcoded constants, stocks from JSON). Replaces split SectorEtfRegistry + StockSymbolRegistry.
- **DailyPriceProvider**: OHLCV-first, Finnhub-fallback layer for daily closing prices. Consumers (EMA, BB, RS, ROC) don't know which data source is used.
- **VFI daily at 9:00 CET**: VFI uses daily OHLCV bars, so hourly would repeat identical data. Pre-market timing gives signal before trading starts.
- **Delete-before-send pattern**: Used by hourly reports (BB, RSI) to keep Telegram clean. Daily reports (VFI, EMA, Tail Risk) use regular `sendMessage()`.
- **`TelegramGateway` interface**: All Telegram-sending components inject the interface for profile-based switching.
- **`quant` package**: All quantitative analysis components live in `org.tradelite.quant`.

## Next Steps
- **#258**: Intraday OHLCV + intraday VFI zero-line cross detection (future enhancement, would enable hourly VFI)
- **#265**: Setup Twelve Data API key in production
- **#257**: Bug: ETF symbols can be added to stock symbols list, causing duplicate tracking
- Consider MACD indicator (uses same EMA concepts from StatisticsUtil)
- Monitor API rate limits with Twelve Data (8 req/min, 800/day)
