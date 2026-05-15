# Product Context

This document explains why this project exists, the problems it solves, how it should work, and the user experience goals.

## Problem

Active investors need continuous monitoring of multiple market dimensions:
- Price levels against buy/sell targets (stocks, crypto, international equities)
- Technical indicators (RSI, EMA structure, Bollinger Bands, VFI)
- Institutional activity signals (insider trades, volume-driven accumulation)
- Sector rotation patterns revealing money flow between growth/defensive sectors
- Momentum and relative strength shifts across sectors and individual stocks

Manual monitoring is impractical — it requires watching dozens of symbols across multiple exchanges and timeframes. This bot automates comprehensive market surveillance and delivers actionable Telegram alerts.

## How it Works

Spring Boot application running continuously via scheduled tasks. All data persisted in SQLite (11 tables). Feature toggles gate individual analysis modules.

### Real-Time Monitoring (Every 5 minutes, US market hours)
- **Stock price evaluation** — Finnhub live prices for all domestic stocks/ETFs, target price alerts, high-change (>5%) alerts
- **International price evaluation** — Yahoo Finance intraday quotes for German (XETRA) and Korean (KRX) stocks, per-exchange market hours gating
- **Relative Strength crossovers** — sector ETF RS vs SPY, real-time crossover alerts
- **Momentum ROC crossovers** — sector ETF ROC10/ROC20 zero-line detection
- **EMA pullback buy alerts** — stocks below EMA 9/21 but above EMA 50/100/200 with positive RS + VFI, 8-hour cooldown

### Hourly Reports (US market hours)
- **Bollinger Band analysis** — band touch + squeeze detection for sectors and stocks (delete-before-send pattern)
- **RSI monitoring** — batched overbought/oversold alerts

### Daily Reports
- **VFI + RS combined** (09:00 CET) — traffic-light classification (GREEN/YELLOW/RED) per symbol
- **Earnings calendar** (08:15 CET) — 7-day look-ahead via Finnhub
- **Accumulation detection** (10:00 CET) — EMA9 < EMA21 + positive rising VFI, with streak counter showing consecutive signal days
- **Tail risk** (13:00 CET) — kurtosis + skewness analysis for fat tail detection
- **Bollinger Band summary** (15:40 CET) — daily BB report
- **EMA classification** (15:50 CET) — price vs 5 EMAs (green/yellow/red)
- **Sector RS summary** (16:00, 21:00 CET) — RS streak tracking, performance comparison
- **OHLCV fetch** (23:00 CET) — Twelve Data backfill for all symbols (400 data points)

### Weekly/Monthly
- **Insider trading report** (Saturday 12:00 CET) — Finnhub insider transactions
- **API usage report** (1st of month) — metering summary for all providers

### Crypto Monitoring (24/7, every 7 minutes)
- CoinGecko price monitoring against targets

## Telegram Integration

Users interact via commands:
- `/set buy/sell <symbol> <price>` — configure target prices
- `/show stocks/coins/all` — display watchlist with targets
- `/rsi <symbol>` — get current RSI value
- `/add <TICKER> <Name>` — add symbol to watchlist
- `/remove <TICKER>` — remove symbol

All alerts and reports delivered as Telegram messages (no user action required).

## Data Architecture

- **SQLite** (11 tables via JdbcTemplate): price quotes, OHLCV (400 days), momentum ROC state, RS crossover state, ignored symbols, sector RS streaks, insider transactions, industry performance, target prices, tracked symbols, API metering, accumulation streaks
- **In-memory**: LivePriceCache (ConcurrentHashMap), AtomicInteger API counters (flushed every 10 min)
- **Feature toggles**: JSON-persisted, runtime-toggleable per analysis module

## External Data Sources

| Provider | Data | Auth | Rate Limit |
|----------|------|------|------------|
| Finnhub | Live stock prices, insider trades, market holidays, earnings calendar | API key | — |
| CoinGecko | Crypto prices | None | — |
| Twelve Data | Daily OHLCV (400 data points) | API key | 8 req/min |
| Yahoo Finance | International OHLCV + intraday quotes (via curl/ProcessBuilder) | None | 3s delay |
| FinViz | Industry sector performance (web scraping) | None | — |

## Tracked Symbols

### Sector & Thematic ETFs (20 total)
**Broad Sectors (11 SPDR):** XLK, XLF, XLE, XLV, XLY, XLP, XLI, XLC, XLRE, XLB, XLU
**Thematic (9):** SMH (Semiconductors), URA (Uranium), SHLD (Cybersecurity), IGV (Software), XOP (Oil & Gas), XHB (Homebuilders), ITA (Aerospace & Defense), XBI (Biotech), TAN (Solar)

### International Stocks
- German XETRA: RHM.DE (Rheinmetall), ENR.DE (Siemens Energy)
- Korean KRX: 005930.KS (Samsung), 000660.KS (SK Hynix)

### Individual Stocks
Dynamically managed via Telegram `/add` and `/remove` commands. Stored in SQLite.

## User Experience Goals

1. **Set and forget** — configure targets once, receive alerts indefinitely
2. **Multi-dimensional awareness** — price, volume, momentum, sector context in one system
3. **Signal persistence** — streak counters and historical context (not just point-in-time alerts)
4. **Minimal noise** — feature toggles, cooldown periods, consolidation (one message per report, not per symbol)
5. **International coverage** — German and Korean stocks monitored alongside US equities
6. **Dev-friendly** — local dev profile with manual job triggers, synthetic data seeding, smoke tests

## Dev Tooling

- `DevJobController` — 18 manual trigger endpoints + phased `run-all` composite
- `DevDataSeeder` — seeds SQLite with 400 days OHLCV, price quotes, sector data, price cache
- Bruno API collection for local testing
- `scripts/run-smoke-test.sh` for pre-deployment validation
