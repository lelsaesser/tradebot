# Active Context

## Current Work Focus

### Fix RS/VFI EMA Warm-up + Decouple Price Cache (#345, #332, #331) (April 30, 2026) — COMPLETE

**#345 — Fix RS vs SPY and VFI calculation discrepancies (EMA warm-up):**
- RS: `RS_LOOKBACK_DAYS` 80 → 400 (~230 recursive EMA steps vs 6)
- VFI: `LOOKBACK_CALENDAR_DAYS` 200 → 400, `numWindows` changed from `SIGNAL_LENGTH + 1` to `Math.max(lastWindowEnd - LENGTH, SIGNAL_LENGTH + 1)` (~150 VFI windows for signal EMA)
- Aligns with TradingView Pine Script per-bar VFI computation
- Tests updated + new test with 280 records proving signal EMA stabilization

**#332 — Decouple price cache from TargetPriceProvider:**
- `FinnhubPriceEvaluator.evaluatePrice()` refactored into two loops:
  - Loop 1: Fetch & cache prices for ALL symbols (stocks + ETFs) via `symbolRegistry.getAll()`
  - Loop 2: Evaluate target prices using cached data (no API calls)
- All symbols now get Finnhub prices cached, persisted to SQLite, and high price change alerts
- `PullbackBuyTracker` "no cached price" log changed to `warn` (canary)

**#331 — Remove verbose PullbackBuyTracker INFO log:**
- "Skipping X — no pullback pattern" changed from `log.info` to `log.debug`

**Build:** 932 tests pass, spotless clean.
