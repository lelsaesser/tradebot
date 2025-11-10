# Active Context

This document tracks the current work focus, recent changes, next steps, active decisions, important patterns, and project insights.

## Current Work Focus
The RSI command feature has been fully implemented and enhanced with price caching capabilities. The system now supports on-demand RSI queries through the Telegram `/rsi` command.

## Recent Changes
- **RSI Command Implementation**: Fully implemented the `/rsi` Telegram command for on-demand RSI queries
- **RSI Price Caching**: Enhanced `RsiService` to use cached current prices from `FinnhubPriceEvaluator` and `CoinGeckoPriceEvaluator` for more accurate real-time RSI calculations
- **RSI Trend Display**: Added RSI change indicators (e.g., `(+2.5)`) to Telegram notifications showing trend from previous calculation
- **Code Coverage**: Increased test coverage to 99% instruction coverage requirement
- **Code Formatting**: Integrated Spotless with Google Java Format (AOSP style) for consistent code formatting
- **Crypto Market Monitoring**: Re-enabled crypto market monitoring in the scheduler

## Next Steps
- Monitor RSI command usage and caching performance in production
- Continue enhancing test coverage where needed

## Future Improvements
- The `stockMarketMonitoring` scheduler should be updated to use a cron expression instead of a fixed-rate delay for more fine-grained control
- Add a weekly report scheduler for API usage monitoring to track costs and rate limits
- Implement dynamic polling frequency adjustment based on market activity to optimize resource usage
- Enhance `TelegramCommandDispatcher` to support more complex command patterns and arguments
- Create a dashboard to visualize bot activity, including trades, alerts, and errors
- Consider adding more Telegram commands (e.g., `/show` for watchlist, `/add` and `/remove` for symbol management)

## Active Decisions
- Chose to implement graceful error handling rather than trying to modify production configuration files
- Decided to silently skip invalid symbols to maintain system stability while processing valid ones

## Important Patterns and Preferences
- The project uses a scheduler-based approach to orchestrate tasks
- Components are loosely coupled using dependency injection
- Error handling is centralized in the `RootErrorHandler`
- **Graceful degradation**: When configuration contains invalid data, skip invalid entries and continue processing valid ones
- **Price caching**: Price evaluators maintain in-memory caches of last fetched prices for real-time calculations
- **Market holiday detection**: System detects and skips duplicate prices on market holidays to maintain data integrity
- **High code quality standards**: 99% code coverage requirement enforced via JaCoCo, consistent formatting via Spotless

## Learnings and Project Insights
- The project is a mature trading bot with a clear, modular structure
- Spring Boot's scheduling features effectively orchestrate complex workflows
- **Critical insight**: Configuration files in production may contain symbols not defined in enums, requiring resilient error handling
- The `StockSymbol` enum acts as a whitelist - only symbols defined there can be processed for insider tracking
- The system should handle configuration mismatches gracefully rather than failing completely
- **RSI calculations benefit from real-time price data**: Using cached current prices alongside historical data provides more accurate on-demand RSI values
- **Price data quality matters**: Market holiday detection prevents data pollution from duplicate prices
- **Command pattern scales well**: The Telegram command dispatcher architecture easily accommodates new commands
