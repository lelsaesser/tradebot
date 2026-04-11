# Plan: Dashboard UI

> Source PRD: lelsaesser/tradebot#233 — Dashboard UI for Tradebot

## Architectural decisions

Durable decisions that apply across all phases:

- **Frontend location**: `dashboard/` at repo root — standalone Vite project with its own `package.json`, not managed by Maven.
- **Backend package**: `org.tradelite.web.dashboard` — all new dashboard controllers and SSE infrastructure live here.
- **Server port**: Spring Boot runs on `9090` (existing). Vite dev server runs on `5173` (default). Vite proxy forwards `/api` → `localhost:9090` in dev, eliminating CORS complexity during development.
- **REST base path**: All dashboard REST endpoints are under `/api/`.
- **SSE endpoint**: `GET /api/events` — single persistent SSE stream, all event types multiplexed on it with named event fields.
- **SSE event naming**: Snake-case event type names map 1:1 to report/alert types: `price-alert`, `price-swing`, `rsi-report`, `bb-alert`, `bb-daily-report`, `ema-report`, `sector-rs-crossover`, `sector-rs-daily`, `sector-roc-alert`, `tail-risk-alert`, `tail-risk-report`, `sector-rotation-alert`, `sector-rotation-daily`, `insider-report`, `error-alert`.
- **Data caching strategy**: Each tracker/service stores its most recent result in memory (simple field, no TTL). REST read endpoints serve this cached value. Cache resets on app restart — acceptable for a local tool.
- **Update model**: SSE for live push (server → client) + REST `GET` for initial page load per panel. No polling.
- **Command endpoints**: Dashboard command endpoints (`POST /api/symbols`, `DELETE /api/symbols/{ticker}`, `POST /api/targets`, `GET /api/rsi/{symbol}`) delegate directly to the same service layer used by Telegram command processors. No duplicated logic.
- **Profiles**: All dashboard controllers and `DashboardEventPublisher` are active in both default and `dev` profiles (unlike `DevJobController` which is `dev`-only).
- **Frontend tech stack**: React 18 + Vite + TypeScript. Tailwind CSS for styling. Dark theme. Native `EventSource` API for SSE — no third-party SSE library needed.
- **Layout**: Single-page dashboard. Fixed header, responsive CSS grid of panels. No client-side routing.
- **Key data models** (backend DTOs, returned as JSON):
  - `WatchlistResponse` — list of symbols with display name, current price, buy target, sell target
  - `RsiReportResponse` — list of RSI signals (symbol, value, previous, diff, zone)
  - `BollingerReportResponse` — list of BB analyses (symbol, %B, bandwidth, bandwidth percentile, signals)
  - `EmaReportResponse` — list of EMA statuses (symbol, price, EMAs, signal color)
  - `SectorRsResponse` — ranked list of ETFs with RS deviation and streak
  - `SectorRocResponse` — list of ETFs with ROC₁₀, ROC₂₀, crossover signals
  - `TailRiskReportResponse` — list of ETFs with kurtosis, skewness, risk level
  - `SectorRotationResponse` — sectors rotating in / rotating out with Z-scores
  - `SectorPerformanceResponse` — top/bottom daily and weekly industry performers
  - `InsiderReportResponse` — table of symbols with buy/sell counts and deltas
  - `DashboardEvent` — SSE envelope: `{ type, timestamp, payload }`

---

## Phase 1: Scaffolding — React frontend + Spring Boot SSE skeleton

**User stories**: 1, 3, 26

### What to build

Create the React + Vite + TypeScript project in `dashboard/` with a single placeholder panel that says "Connected" or "Disconnected" based on SSE state. On the Spring Boot side, add `DashboardEventPublisher` and `SseController`. Wire a heartbeat timer in `SseController` that emits a `ping` event every 30 seconds so the connection stays alive and the browser can verify connectivity. No real data flows yet — the goal is a proven end-to-end SSE pipeline and the dev workflow (Vite proxy → Spring Boot) working correctly.

### Acceptance criteria

- [ ] `dashboard/` directory exists with a valid Vite + React + TypeScript project that starts with `npm run dev`.
- [ ] Vite proxy config forwards `/api` requests to `localhost:9090`.
- [ ] `GET /api/events` returns a valid `text/event-stream` response.
- [ ] The browser `EventSource` connects to `/api/events` and receives heartbeat `ping` events.
- [ ] The dashboard header shows a green "Live" indicator when SSE is connected and a red "Disconnected" indicator when the connection is lost.
- [ ] `DashboardEventPublisher.publish(String eventType, Object payload)` is injectable by any service and fans out to all active `SseEmitter` connections.
- [ ] `SseController` cleans up dead emitters on timeout or client disconnect.
- [ ] Unit test: `DashboardEventPublisher` — verify `publish()` sends correct event type and payload to registered emitters.

---

## Phase 2: Watchlist & price monitoring panel

**User stories**: 2, 4, 5, 6, 18, 19, 20, 22, 27

### What to build

Add a Watchlist panel to the dashboard showing all monitored stocks and crypto with their current prices and buy/sell targets. Wire `BasePriceEvaluator`, `FinnhubPriceEvaluator`, and `CoinGeckoPriceEvaluator` to also publish SSE events when a buy alert, sell alert, or high daily swing alert fires. Add REST command endpoints so the user can add a symbol, remove a symbol, and set a buy/sell target directly from the dashboard — delegating to the same service layer the Telegram command processors use. Inline error messages appear in the form when a command fails.

**REST endpoints added this phase:**
- `GET /api/watchlist` — returns current stocks + crypto with prices and targets
- `POST /api/symbols` — add symbol (body: ticker, displayName)
- `DELETE /api/symbols/{ticker}` — remove symbol
- `POST /api/targets` — set buy or sell target (body: ticker, side, price)

**SSE events added this phase:** `price-alert`, `price-swing`

### Acceptance criteria

- [ ] Watchlist panel renders all stocks and crypto from `GET /api/watchlist` on page load.
- [ ] Add symbol form submits to `POST /api/symbols`, updates the watchlist on success, shows inline error on failure.
- [ ] Remove symbol button calls `DELETE /api/symbols/{ticker}`, removes the row on success.
- [ ] Set target form submits to `POST /api/targets`, updates the displayed target on success.
- [ ] When a buy or sell alert fires in the backend, a `price-alert` SSE event is received and the relevant row highlights.
- [ ] When a high daily swing alert fires, a `price-swing` SSE event is received and shown in the panel.
- [ ] `GET /api/watchlist` returns a cached price (the last price the evaluator saw) — it does not trigger a fresh API fetch.
- [ ] Controller tests: each REST endpoint tested with `@WebMvcTest`, mocked services, verifying status codes and response shape.
- [ ] Frontend component tests: given a `WatchlistResponse` payload, the panel renders correct symbol rows.

---

## Phase 3: Technical signals panels — RSI, Bollinger Bands, EMA

**User stories**: 7, 8, 9, 21, 28

### What to build

Add three panels: RSI Signals, Bollinger Bands, and EMA Status. Each panel fetches its latest cached report on load via REST and updates live via SSE when the hourly job fires. Wire `RsiService`, `BollingerBandTracker`, and `EmaTracker` to publish SSE events alongside their Telegram messages. Add an on-demand RSI query form that calls `GET /api/rsi/{symbol}` and shows the result inline without a page reload.

**REST endpoints added this phase:**
- `GET /api/rsi/report` — latest RSI signal report (cached)
- `GET /api/rsi/{symbol}` — on-demand RSI for a single symbol
- `GET /api/bollinger` — latest Bollinger Band report (cached)
- `GET /api/ema` — latest EMA report (cached)

**SSE events added this phase:** `rsi-report`, `bb-alert`, `bb-daily-report`, `ema-report`

### Acceptance criteria

- [ ] RSI panel loads from `GET /api/rsi/report` on page load, showing overbought and oversold sections.
- [ ] On-demand RSI form calls `GET /api/rsi/{symbol}` and displays the result inline; handles "not enough data" gracefully.
- [ ] Bollinger Band panel loads from `GET /api/bollinger` on page load, showing %B, bandwidth, and signal type per symbol.
- [ ] EMA panel loads from `GET /api/ema` on page load, showing green/yellow/red status per symbol.
- [ ] When the hourly job fires, `rsi-report`, `bb-alert`, and `ema-report` SSE events update the corresponding panels live.
- [ ] Each panel shows a last-updated timestamp sourced from the SSE event or REST response.
- [ ] `RsiService`, `BollingerBandTracker`, and `EmaTracker` each store their most recent result in memory for the REST read endpoints to serve.
- [ ] Controller tests for all four endpoints. Frontend component tests for all three panels.

---

## Phase 4: Sector analysis panels — RS, ROC, Tail Risk, Rotation, Performance

**User stories**: 10, 11, 12, 13, 14, 15

### What to build

Add four sector-focused panels: Sector Relative Strength (20 ETFs ranked, split broad/thematic with streak counts), Sector Momentum ROC (ROC₁₀/ROC₂₀ per ETF with crossover alerts), Tail Risk (kurtosis/skewness per ETF with risk level and directional bias), and Sector Rotation (Z-score analysis — money flowing in/out) plus the FinViz daily sector performance top/bottom lists. Wire `SectorRelativeStrengthTracker`, `SectorMomentumRocTracker`, `TailRiskTracker`, and `SectorRotationTracker` to publish SSE events.

**REST endpoints added this phase:**
- `GET /api/sectors/rs` — latest sector RS daily summary (cached)
- `GET /api/sectors/roc` — latest sector ROC state (cached)
- `GET /api/sectors/tail-risk` — latest tail risk report (cached)
- `GET /api/sectors/rotation` — latest sector rotation Z-score analysis (cached)
- `GET /api/sectors/performance` — latest FinViz daily sector performance (cached)

**SSE events added this phase:** `sector-rs-crossover`, `sector-rs-daily`, `sector-roc-alert`, `tail-risk-alert`, `tail-risk-report`, `sector-rotation-alert`, `sector-rotation-daily`

### Acceptance criteria

- [ ] Sector RS panel loads on page load, ranks all 20 ETFs with RS deviation and streak, split into broad sectors and thematic ETFs sections.
- [ ] When a real-time RS crossover fires during market hours, a `sector-rs-crossover` SSE event updates the panel immediately.
- [ ] Sector ROC panel loads on page load, shows ROC₁₀ and ROC₂₀ per ETF; crossover alerts highlighted.
- [ ] Tail Risk panel loads on page load, shows kurtosis/skewness with color-coded risk level (LOW/MODERATE/HIGH/EXTREME) and crash/rally directional bias.
- [ ] Sector Rotation panel loads on page load, shows sectors rotating in vs out with Z-scores.
- [ ] Sector Performance panel loads on page load, shows top/bottom daily and weekly industry performers from the latest FinViz scrape.
- [ ] All five panels update live when their corresponding SSE events arrive.
- [ ] All trackers store their most recent result in memory for REST read endpoints.
- [ ] Controller tests for all five endpoints. Frontend component tests for all four panels.

---

## Phase 5: Insider trading report + activity feed

**User stories**: 16, 17

### What to build

Add an Insider Trading panel showing the latest weekly report as a sortable table (symbol, sell count, buy count, sell delta, buy delta). Add an Activity Feed panel that accumulates all SSE events received since the page was loaded in reverse-chronological order, showing the event type, a human-readable summary, and a timestamp. The feed is purely in-memory in the browser — it resets when the page is refreshed. Wire `InsiderTracker` to publish SSE events.

**REST endpoints added this phase:**
- `GET /api/insider` — latest insider trading report (cached)

**SSE events added this phase:** `insider-report`

### Acceptance criteria

- [ ] Insider Trading panel loads from `GET /api/insider` on page load, displaying a table with symbol, buy count, sell count, and deltas from the previous week.
- [ ] When the weekly insider job fires, an `insider-report` SSE event updates the panel live.
- [ ] Activity Feed panel shows a running log of all SSE events received since page load in reverse-chronological order.
- [ ] Each feed entry shows: event type label, a short human-readable summary of the payload, and a relative or absolute timestamp.
- [ ] Feed is browser-only — resets on page refresh, no persistence required.
- [ ] `InsiderTracker` stores its most recent report in memory for `GET /api/insider` to serve.
- [ ] Controller test for `GET /api/insider`. Frontend component test: given a sequence of SSE events, feed renders entries in correct order.

---

## Phase 6: UX polish — timestamps, highlight on update, visual indicators

**User stories**: 23, 24, 25

### What to build

Add visual polish across all panels: last-updated timestamps on every panel header (sourced from SSE event payload or REST response), a brief highlight/flash animation on a panel when it receives a new SSE event, and consistent color coding and iconography for signal direction (bullish green / bearish red / neutral grey) and alert severity across all panels. No new data endpoints or SSE events are added in this phase — this is purely frontend work.

### Acceptance criteria

- [ ] Every panel header shows "Last updated: <relative time>" that updates when a new SSE event arrives for that panel.
- [ ] When any panel receives an SSE update, it briefly flashes or highlights to draw the user's attention, then returns to its normal state.
- [ ] All signal directions use consistent color coding: green for bullish/outperforming/oversold, red for bearish/underperforming/overbought, grey/yellow for neutral/mixed.
- [ ] All alert severity levels (EXTREME, HIGH, MODERATE, LOW / overbought, oversold / UPPER_BAND_TOUCH, LOWER_BAND_TOUCH, SQUEEZE) have consistent icons and colors across every panel.
- [ ] Buy alerts and sell alerts in the price panel are visually distinct from each other (e.g. rocket icon for buy, coin icon for sell).
- [ ] Error states (failed command, no data yet) are visually distinct from normal empty states.
- [ ] Frontend component tests updated to assert color/icon rendering for key signal states.
