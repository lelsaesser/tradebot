# Active Context

## Current Work Focus

### EMA Pullback Buy Alert (#307) (April 21, 2026) — IN PROGRESS
Implementing real-time EMA pullback buy alerts. Sends Telegram alerts when a stock pulls back below EMA 9/21 but stays above EMA 50/100/200, with positive RS vs SPY and VFI confirmation.

**New Files:**
- `quant/PullbackBuyTracker.java` — detects pullback patterns, sends per-stock alerts via TelegramGateway
- `quant/PullbackBuyTrackerTest.java` — 22 tests

**Modified Files:**
- `core/IgnoreReason.java` — added `ttlSeconds` field for per-reason TTL, new `PULLBACK_BUY_ALERT` value (8h cooldown)
- `common/TargetPriceProvider.java` — `isSymbolIgnored()` uses `reason.getTtlSeconds()` instead of global constant
- `common/FeatureToggle.java` — added `PULLBACK_BUY_ALERT`
- `config/feature-toggles.json` — added `pullbackBuyAlert: true`
- `Scheduler.java` — PullbackBuyTracker injected, runs after `evaluatePrice()` in `stockMarketMonitoring()`, manual method added
- `DevJobController.java` — `POST /dev/jobs/pullback-buy-alert` endpoint, added to `runAll()` Phase 3
- `DevDataSeeder.java` — seeds OHLCV for all bundle symbols (not just first 10), seeds price cache from last closing price, crafts a pullback price for first qualifying stock using OHLCV-based EMAs, injected `FinnhubPriceEvaluator` for cache access

**Key Design Decisions:**
- **No separate Finnhub API calls** — reuses `FinnhubPriceEvaluator.lastPriceCache` (populated every 5 min). Principle: Finnhub is the data ingestion layer, all indicators consume from cache/SQLite.
- **Strict EMA conditions** — ALL of EMA 50/100/200 must be above price. Highest quality setups only.
- **8-hour cooldown** — effectively once per trading day (6.5h session), allows re-alert next morning if pullback persists.
- **Per-reason TTL** — `IgnoreReason` enum now has `ttlSeconds` field. Existing reasons keep 12h, pullback gets 8h.
- **One message per stock, no emoji:** `Potential buy for Broadcom (AVGO) at $178.50. 21 EMA pullback while volume and relative strength stay bullish`
- **Static wording** — always "21 EMA pullback" regardless of actual pullback depth.

**Status:** Feature code complete, tests passing (926 total), debugging dev seeder to produce a visible alert in dev mode. The dev seeder race condition (scheduler fires before seeding completes) is a pre-existing issue, not introduced by this feature.
