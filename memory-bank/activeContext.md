# Active Context

## Current Work Focus

### Fix market holiday detection false positives (#333) (May 3, 2026) — COMPLETE
Replaced the per-symbol `isPotentialMarketHoliday()` heuristic (which caused false positives for illiquid ETFs) with an authoritative Finnhub-based holiday calendar.

**Key Changes:**
- New `MarketHolidayService`: fetches `/stock/market-holiday?exchange=US` at startup, caches ~5 years of holidays in memory
- Handles full closures AND early-close days (e.g., day after Thanksgiving 09:30-13:00)
- Retry every 5 min if fetch fails; falls back to weekday+hours while cache empty
- `Scheduler`: replaced `DateUtil.isStockMarketOpen()` with `marketHolidayService.isMarketOpen()`
- `FinnhubPriceEvaluator`: removed `isPotentialMarketHoliday()`, persistence guarded by `marketHolidayService.isMarketOpen(null)` (defense in depth)
- `FinnhubClient`: added `getMarketHolidays()` method + `MarketHolidayResponse` DTO

**Build:** 951 tests pass, spotless clean.
