# SSE state lives in React context, not prop drilling

`SSEProvider` owns the single `EventSource` instance and exposes connection status and per-event subscriptions via `useSSE()`. Components read SSE state directly from context rather than receiving it as props from `App`.

Phase 1 only has `Header` consuming SSE state, which would not require context. We chose context because Phase 2–6 panels each subscribe to different event types from the same stream. Prop-drilling SSE callbacks from `App` through a layout layer to every panel would require every intermediate component to carry props it does not use. Context also enforces a single `EventSource` instance — recreation per component would cause duplicate connections.

## Considered Options

**Prop drilling from `App.tsx`** — rejected. Clean for Phase 1 but becomes unworkable as panels multiply. Refactoring mid-project would touch every panel component.
