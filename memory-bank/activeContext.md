# Active Context

## Current Work Focus

### Accumulation Detection Signal (#371) (May 9, 2026) — COMPLETE
Added institutional accumulation detection: identifies stocks where price is weak but volume flow is bullish, signaling pre-trend institutional positioning.

**Signal Logic (required conditions, AND):**
- Price weak: EMA9 < EMA21 (no established uptrend)
- Volume bullish: VFI > 0 AND VFI > signal line (net buying pressure accelerating)

**Informational Context (displayed, not gating):**
- RS vs SPY value + slope direction (↑/→/↓)
- RS EMA value + slope direction (↑/→/↓)
- Slope: 5-day lookback with ±0.5% dead zone

**New Classes:**
| Class | Package | Purpose |
|-------|---------|---------|
| `TrendDirection` | `org.tradelite.quant` | Enum: RISING, FLAT, FALLING |
| `RsTrendResult` | `org.tradelite.service` | Record: rsValue, rsEma, rsTrend, rsEmaTrend |
| `AccumulationSignal` | `org.tradelite.core` | Record: all data for alert formatting |
| `AccumulationDetectionService` | `org.tradelite.core` | Pure logic: evaluate(EmaAnalysis, VfiAnalysis, RsTrendResult) → Optional |
| `AccumulationDetectionTracker` | `org.tradelite.core` | Orchestration: iterates stocks, calls services, formats + sends Telegram alert |

**Modified Classes:**
- `RelativeStrengthService` — added `getRsTrend(String symbol)` with 5-day slope detection
- `Scheduler` — added 10:00 CET MON-FRI cron + manual trigger
- `PullbackBuyTracker` — added `log.warn()` when EMA/VFI analysis returns empty
- `DevJobController` — added `/dev/jobs/accumulation-detection` endpoint + run-all integration
- `FeatureToggle` — added `ACCUMULATION_DETECTION("accumulationDetection")`
- `feature-toggles.json` — added `"accumulationDetection": true`

**Alert Format:**
```
*Institutional accumulation detected*

*Nvidia (NVDA)*
  Price: $112.40 | EMA9: $111.80 < EMA21: $115.20
  VFI: +3.42 | Signal: +2.18
  RS vs SPY: 1.0523 ↑ | EMA: 1.0480 ↑

Based on EMA crossdown + positive rising VFI
```

**Design Decisions:**
- No cooldown — fires daily for all qualifying stocks; add suppression later if noisy
- RS is informational only (not a gating condition)
- Runs on all stocks (domestic + international)
- Warning logs on data skip to surface persistent data gaps via scan-logs.sh
- Pure service/tracker separation: service holds only signal logic (testable without mocking services), tracker handles orchestration
- `TrendDirection` in `quant` package for reuse across indicators

**Build:** 1009 tests pass, spotless clean, coverage met.

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
