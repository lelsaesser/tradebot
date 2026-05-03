# Active Context

## Current Work Focus

### Earnings Calendar Alerts (#363) (May 3, 2026) — COMPLETE
Added a daily Telegram report listing upcoming earnings for tracked stocks in the next 7 calendar days, using Finnhub's `/calendar/earnings` endpoint.

**Key Changes:**
- New `EarningsCalendarResponse` DTO for Finnhub earnings calendar API
- New `EarningsCalendarTracker`: fetches earnings in 7-day window, filters against tracked stocks, sends condensed grouped-by-date Telegram message
- `FinnhubClient`: added `getEarningsCalendar(from, to)` method
- `Scheduler`: daily cron at 08:15 CET weekdays + manual trigger
- `FeatureToggle`: added `EARNINGS_CALENDAR_ALERT`
- `DevJobController`: added `/dev/jobs/earnings-calendar` endpoint + included in run-all smoke test
- Bruno collection: added `earningsCalendar.yml`

**Design Decisions:**
- Condensed format (one line per stock: `• DisplayName (TICKER)`)
- Stocks only (no ETFs), silent when empty
- Single batch API call (~22/month), no dedup table needed (daily report is idempotent)
- No `/earnings` Telegram command (cron is sufficient)

**Build:** 942+ tests pass, spotless clean, coverage met.
