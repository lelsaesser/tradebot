# Progress

This document tracks what works, what's left to build, the current status, known issues, and the evolution of project decisions.

## What Works
The application is fully functional with the following implemented features:

### Core Monitoring
- **Stock Market Monitoring**: Fetches prices from Finnhub API, evaluates against target prices, sends Telegram alerts
- **Crypto Market Monitoring**: Fetches prices from CoinGecko API, evaluates against target prices, sends Telegram alerts
- **Price Caching**: Both price evaluators maintain in-memory caches for real-time calculations
- **RSI Data Collection**: Daily fetching of historical closing prices for RSI calculations
- **Weekly Insider Trading Reports**: Tracks and reports insider transactions from Finnhub

### RSI Features
- **Automated RSI Notifications**: Sends alerts when RSI enters overbought (≥70) or oversold (≤30) zones
- **RSI Trend Indicators**: Shows RSI change from previous calculation (e.g., `(+2.5)`)
- **On-Demand RSI**: `/rsi` Telegram command provides current RSI value for any symbol
- **Market Holiday Detection**: Skips duplicate prices on market holidays to maintain data quality
- **Real-Time RSI Calculation**: Uses cached current prices combined with historical data

### Telegram Integration
- **Command Processing**: Full command dispatcher pattern with multiple commands:
  - `/rsi <symbol>`: Get current RSI value
  - `/add`, `/remove`: Symbol management (implementation exists)
  - `/show`: Display watchlist (implementation exists)
  - `/set`: Configuration management (implementation exists)
- **Message Polling**: Continuous polling for new Telegram messages
- **Alert System**: Automated notifications for price targets and RSI zones

### System Features
- **Error Handling**: Centralized via `RootErrorHandler` for all scheduled tasks
- **Graceful Degradation**: Handles invalid configuration data without crashing
- **API Metering**: Tracks API usage to stay within rate limits
- **JSON Persistence**: Stores RSI data, target prices, and insider transactions in config files
- **High Test Coverage**: 99% instruction coverage enforced by JaCoCo
- **Consistent Code Style**: Google Java Format (AOSP) enforced by Spotless

## What's Left to Build
- **API Usage Reporting**: Weekly scheduler to generate API usage reports for cost monitoring
- **Cron-Based Scheduling**: Convert stock market monitoring from fixed-rate to cron expressions
- **Dynamic Polling**: Adjust polling frequency based on market activity
- **Enhanced Command Dispatcher**: Support for more complex command patterns and arguments
- **Activity Dashboard**: Visualization of bot activity, trades, alerts, and errors
- **Additional Configuration Options**: More fine-grained control over monitoring parameters

## Current Status
The project is in a mature, production-ready state with all core features implemented and tested. Recent work focused on:
- RSI command implementation with price caching
- Increasing test coverage to 99%
- Adding RSI trend indicators
- Code formatting standardization
- Re-enabling crypto market monitoring

The system is actively being used and monitored in production.

## Known Issues
No critical issues at this time. Minor future improvements identified in `activeContext.md`.

## Evolution of Project Decisions
- **Initial Architecture**: Started with combined market monitoring, later split into separate stock/crypto schedulers for better control
- **RSI Enhancement**: Evolved from basic RSI calculations to include market holiday detection, trend indicators, and real-time caching
- **Command Pattern**: Telegram command processing evolved from simple message handling to a full Command pattern implementation
- **Code Quality**: Progressively increased coverage requirements from initial implementation to 98% to current 99%
- **Error Handling**: Moved from failing on invalid data to graceful degradation approach
- **Testing Strategy**: Comprehensive unit tests with high coverage requirements ensure reliability
