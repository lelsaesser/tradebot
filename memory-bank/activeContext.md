# Active Context

This document tracks the current work focus, recent changes, next steps, active decisions, important patterns, and project insights.

## Current Work Focus
The scheduler has been updated to run cryptocurrency monitoring every 7 minutes, while stock monitoring remains at 5 minutes.

## Recent Changes
- **Separated Schedulers**: The combined market monitoring job was split into `stockMarketMonitoring` and `cryptoMarketMonitoring`.
- **Updated Crypto Schedule**: The `cryptoMarketMonitoring` job is now scheduled to run every 7 minutes.
- **Updated Tests**: The `SchedulerTest` class was updated to reflect the new scheduler methods.

## Next Steps
- Monitor the application to ensure the schedulers are running at their new, correct intervals.

## Future Improvements
- The `stockMarketMonitoring` scheduler should be updated to use a cron expression instead of a fixed-rate delay. This will provide more fine-grained control over when the job runs.
- A new scheduler should be added to generate a weekly report of API usage, which will help monitor costs and stay within rate limits.
- A mechanism to dynamically adjust the polling frequency of the schedulers based on market activity could be implemented to optimize resource usage.
- The `TelegramCommandDispatcher` should be enhanced to support more complex command patterns and arguments.
- A dashboard to visualize the bot's activity, including trades, alerts, and errors, would be a valuable addition.

## Active Decisions
- Chose to implement graceful error handling rather than trying to modify production configuration files
- Decided to silently skip invalid symbols to maintain system stability while processing valid ones

## Important Patterns and Preferences
- The project uses a scheduler-based approach to orchestrate tasks
- Components are loosely coupled using dependency injection
- Error handling is centralized in the `RootErrorHandler`
- **New pattern**: Graceful degradation when configuration contains invalid data - skip invalid entries and continue processing valid ones

## Learnings and Project Insights
- The project is a trading bot with a clear, modular structure
- The use of Spring Boot and its scheduling features simplifies the orchestration of complex workflows
- **Critical insight**: Configuration files in production may contain symbols not defined in the enum, requiring resilient error handling
- The `StockSymbol` enum acts as a whitelist - only symbols defined there can be processed for insider tracking
- The system should be designed to handle configuration mismatches gracefully rather than failing completely
