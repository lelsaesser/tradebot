# Active Context

## Current Work Focus

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

**Design Decisions:**
- ProcessBuilder + curl bypasses Yahoo's TLS fingerprint detection (validated in POC)
- Prices stored in local currency (EUR/KRW) — no FX conversion
- SAP stays as NYSE ADR on existing infrastructure
- International detection: any-dot heuristic (`ticker.contains(".")`)
- No Telegram alerts for Yahoo failures — graceful degradation via log-only
- No feature toggle — caught exceptions provide sufficient degradation
- No intraday monitoring for international stocks (Finnhub doesn't serve them)
- `@Generated` on `executeCurl` for JaCoCo coverage exclusion (ProcessBuilder can't be unit tested)
- All indicators (EMA, BB, RS, VFI, Tail Risk, ROC) run on international symbols

**Symbols:**
| Company | Symbol | Exchange | Currency |
|---------|--------|----------|----------|
| Rheinmetall | `RHM.DE` | XETRA | EUR |
| Siemens Energy | `ENR.DE` | XETRA | EUR |
| Samsung Electronics | `005930.KS` | KRX | KRW |
| SK Hynix | `000660.KS` | KRX | KRW |

**Build:** 976+ tests pass, spotless clean, coverage 97%+ met.
