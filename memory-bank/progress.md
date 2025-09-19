# Progress

This document tracks what works, what's left to build, the current status, known issues, and the evolution of project decisions.

## What Works
The basic application structure is in place. The scheduler is configured to run several tasks, including:
- Market monitoring for stocks and cryptocurrencies.
- Daily fetching of RSI data.
- Weekly insider trading reports.
- Telegram message polling.
- Cleanup of ignored symbols.

## What's Left to Build
- The actual implementation of the logic within each scheduled task needs to be verified and potentially expanded.
- The Telegram command processing logic needs to be fully implemented and tested.
- Comprehensive error handling and notification mechanisms need to be put in place.
- The configuration for API keys and other sensitive data needs to be externalized and secured.

## Current Status
The project is in the early stages of development. The core components and scheduling are set up, but the detailed implementation is still in progress.

## Known Issues
There are no known issues at this time.
