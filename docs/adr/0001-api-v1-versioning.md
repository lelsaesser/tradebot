# ADR 0001: Versioned API prefix /api/v1/

**Status:** Accepted  
**Date:** 2026-05-26

## Context

First production REST endpoint added: `GET /api/v1/events`. Prior routes are dev-only (`/dev/jobs/**`). Establishing a versioning convention now costs nothing — no existing frontend consumers — whereas retrofitting it later would require coordinated frontend and backend changes.

## Decision

All production API routes use the `/api/v1/` prefix. Dev/internal routes (`/dev/**`) are exempt.

## Consequences

- Frontend always targets a versioned path; breaking changes can be introduced under `/api/v2/` without disrupting existing clients.
- No migration cost at this point — no frontend yet consumes any production endpoint.
- Future developers follow `/api/v1/` for all new production endpoints by convention.
