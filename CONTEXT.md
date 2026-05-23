# Context

Domain glossary for the tradebot dashboard. Implementation details belong in code; this file is a glossary only.

## Terms

**DashboardEventPublisher**
Spring service that owns the active SSE emitter registry. All server-to-client event fan-out goes through `publish(type, payload)`. No caller touches the emitter list directly.

**SseController**
HTTP layer only. Exposes `GET /api/events`. Creates `SseEmitter` instances and hands them to `DashboardEventPublisher`. Contains no business logic.

**DashboardEvent**
SSE wire envelope: `{ type, timestamp, payload }`. Timestamp is server-side `Instant.now()` at publish time. Sent as JSON in the SSE `data:` field. The SSE `event:` field also carries the type for client-side event routing.

**Heartbeat**
A `ping` `DashboardEvent` (null payload) published every 30 seconds by `DashboardEventPublisher`. Keeps SSE connections alive and lets the frontend verify connectivity.
