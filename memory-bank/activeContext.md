# Active Context

## Current Work Focus

### Accumulation Streak Counter (#404) (May 15, 2026) — COMPLETE
Added a streak counter to institutional accumulation detection alerts showing how many consecutive days the accumulation signal has been active.

**Key Changes:**
- New `AccumulationStreak` record — `symbol`, `streakDays`, `lastUpdated` (LocalDate)
- New `AccumulationStreakRepository` interface — `save()`, `findBySymbol()`, `deleteAllExcept(Set<String>)`
- New `SqliteAccumulationStreakRepository` — JdbcTemplate implementation with `INSERT OR REPLACE` and bulk delete
- New `accumulation_streaks` table in `schema.sql` (symbol PK, streak_days, last_updated)
- `AccumulationDetectionTracker` — added streak update logic: increment existing, create new at day 1, delete ended streaks, idempotent (skip if already updated today)
- Alert message format: `*Chevron (CVX) — 5 days*` for streak > 1; day-1 signals omit streak annotation

**Design Decisions:**
- Same condition for detection and continuation: EMA9 < EMA21 AND VFI > 0 AND VFI > signal
- No separate persistence service (unlike SectorRsStreakPersistence) — repository injected directly into tracker
- `deleteAllExcept(signalingSymbols)` cleans up ended streaks in one query
- Idempotency via `lastUpdated` date check (prevents double-increment on manual re-trigger)

**Build:** 1061 tests pass (1057 unit + 4 integration), spotless clean.

---

### Remove Legacy File Migration Code (#391) (May 15, 2026) — COMPLETE
Removed dead migration code from `ApiRequestMeteringService` after confirming production deployment of #385 was successful.

**Key Changes:**
- Removed `LEGACY_FILES` constant, `migrateLegacyFilesIfNeeded()`, `parseLegacyFile()` methods
- Removed dead imports: `java.io.File`, `java.io.IOException`, `java.nio.file.Files`
- Simplified `startup()` to just call `initializeCounters()`
- Removed 6 migration-specific tests + dead test imports

**Build:** 1049 tests pass, spotless clean. Net: 177 lines deleted.

---
Added real-time price monitoring for international stocks (German XETRA, Korean KRX) via Yahoo Finance's `meta.regularMarketPrice` field. This enables target price alerts, pullback buy alerts, and high-change alerts for international symbols.

**Key Changes:**
- New `LivePriceCache` service — shared `ConcurrentHashMap<String, Double>` extracted from `FinnhubPriceEvaluator`, used by both Finnhub and Yahoo evaluators
- New `YahooPriceQuote` record — DTO for Yahoo quote data (currentPrice, previousClose, dailyHigh, dailyLow, changePercent, timestamp)
- New `YahooPriceEvaluator` — mirrors FinnhubPriceEvaluator: fetches current prices, populates LivePriceCache, evaluates target prices, high-change alerts, persists to SQLite
- `YahooFinanceClient` — added `fetchCurrentPrice()` method extracting `meta.regularMarketPrice` from `/v8/finance/chart/` endpoint
- `MarketStatusService` — added `isExchangeOpen(symbol)` for XETRA (09:00–17:30 Europe/Berlin) and KRX (09:00–15:30 Asia/Seoul)
- `FinnhubPriceEvaluator` — refactored to use `LivePriceCache` (removed internal `lastPriceCache`)
- `PullbackBuyTracker` — now injects `LivePriceCache` directly (no longer depends on FinnhubPriceEvaluator for cache access)
- `Scheduler` — Yahoo evaluator runs every 5 min (handles its own exchange-hours gating, independent of US market hours)
- `DevJobController` — added `/dev/jobs/yahoo-price-evaluation` endpoint (18 jobs total in run-all)
- `DevDataSeeder` — uses `LivePriceCache` instead of `FinnhubPriceEvaluator`

**Design Decisions:**
- Yahoo quotes stored in existing `finnhub_price_quotes` table (no rename, no schema change)
- Per-exchange market hours gating (no holiday detection for international exchanges)
- 3s delay between Yahoo requests (matching existing rate limiting from OhlcvFetcher)
- LivePriceCache is a simple @Service (no feature toggle needed — evaluator only fetches when exchange is open)

**Build:** 1036 tests pass, spotless clean.

---

### API Request Metering SQLite Migration (#379) (May 11, 2026) — COMPLETE
Migrated `ApiRequestMeteringService` from per-call file I/O to periodic SQLite persistence. Eliminates ~100+ file writes per 5-minute cycle.

**Key Changes:**
- Replaced 4 individual `AtomicInteger` fields with `ConcurrentHashMap<String, AtomicInteger>`
- New `ApiMeteringRepository` interface + `SqliteApiMeteringRepository` (batch `INSERT OR REPLACE`)
- New `ApiMeteringRecord` record class in `repository` package
- Added `api_request_metering` table to `schema.sql`
- `flushCounters()` persists all 4 counters in one batch call
- Periodic flush via Scheduler's `periodicMaintenance()` (every 10 min, replaces old `cleanupIgnoreSymbols()`)
- `@PreDestroy` shutdown hook flushes on graceful shutdown
- `resetCounters()` includes immediate flush (prevents race with periodic flush)
- Startup `initializeCounters()` detects stale month → resets to 0 + warns
- Removed file I/O code, `metering.counter-dir` property, 4 `.gitignore` entries

**Design Decisions:**
- 10-min flush interval (acceptable ~10 min data loss on hard crash for rate-limit awareness counters)
- Scheduler orchestrates flush (not self-contained in service)
- `provider TEXT PRIMARY KEY` — one row per provider, overwritten on reset
- Monthly cron kept separate; reset does immediate flush to prevent race
- String constants (not enum) for provider IDs

**Files Modified/Created:**
- `src/main/resources/schema.sql` — added table
- `src/main/java/org/tradelite/repository/ApiMeteringRecord.java` — NEW
- `src/main/java/org/tradelite/repository/ApiMeteringRepository.java` — NEW
- `src/main/java/org/tradelite/repository/SqliteApiMeteringRepository.java` — NEW
- `src/main/java/org/tradelite/service/ApiRequestMeteringService.java` — major refactor
- `src/main/java/org/tradelite/Scheduler.java` — renamed method + added flush
- `.gitignore` — removed 4 counter file entries
- `src/test/java/org/tradelite/service/ApiRequestMeteringServiceTest.java` — rewritten (mocked repo)
- `src/test/java/org/tradelite/repository/SqliteApiMeteringRepositoryTest.java` — NEW
- `src/test/java/org/tradelite/SchedulerTest.java` — updated test
- `src/test/java/org/tradelite/ApplicationTest.java` — removed obsolete property

**Build:** All tests pass, spotless clean.

---

### International Stock Support (#372) (May 7-9, 2026) — COMPLETE
Added support for German (XETRA) and Korean (KRX) stocks via Yahoo Finance, using Java ProcessBuilder + curl to bypass TLS fingerprint blocking that prevents Java's RestTemplate from accessing Yahoo's API.

**Key Changes:**
- New `YahooFinanceClient` — executes `curl --fail -s --connect-timeout 5 --max-time 10 -H "User-Agent: Mozilla/5.0"` via ProcessBuilder, parses OHLCV JSON response
- New `YahooFetchException` — unchecked exception for Yahoo failures, caught silently in OhlcvFetcher (log only, no Telegram alert)
- `SymbolRegistry` — added `isInternationalSymbol()` (any-dot heuristic), `getInternationalStocks()`, `getDomesticStocks()`
- `OhlcvFetcher` — refactored to two-pass loop: domestic (TwelveData, 9s delay) then international (Yahoo, 3s delay)
- `FinnhubPriceEvaluator` — skips international symbols in live price loop
- `ApiRequestMeteringService` — added Yahoo counter + `config/yahoo-monthly-requests.txt`
- `Scheduler` — Yahoo included in monthly API usage report
- `DevDataSeeder` — pre-seeds RHM.DE, ENR.DE, 005930.KS, 000660.KS

**Symbols:**
| Company | Symbol | Exchange | Currency |
|---------|--------|----------|----------|
| Rheinmetall | `RHM.DE` | XETRA | EUR |
| Siemens Energy | `ENR.DE` | XETRA | EUR |
| Samsung Electronics | `005930.KS` | KRX | KRW |
| SK Hynix | `000660.KS` | KRX | KRW |

**Build:** 976+ tests pass, spotless clean, coverage 97%+ met.
