# Active Context

This document tracks the current work focus, recent changes, next steps, active decisions, important patterns, and project insights.

## Current Work Focus
Fixed a bug in the Telegram client that caused messages containing special characters (e.g., '&') to be truncated.

## Recent Changes
- **Fixed TelegramClient.java**: URL-encoded the message content before sending it to the Telegram API to prevent truncation of messages with special characters.
- **Added URL encoding**: Used `URLEncoder.encode()` to ensure that all characters in the message are correctly transmitted.

## Next Steps
- Monitor the application to confirm that the fix resolves the message truncation issue for all stock symbols.
- Review other parts of the code that interact with external APIs to ensure proper encoding of data.

## Active Decisions
- Decided to apply URL encoding at the `TelegramClient` level to provide a centralized fix for all messages.

## Important Patterns and Preferences
- The project uses a scheduler-based approach to orchestrate tasks.
- Components are loosely coupled using dependency injection.
- Error handling is centralized in the `RootErrorHandler`.
- **New pattern**: URL-encode data sent to external APIs to prevent issues with special characters.

## Learnings and Project Insights
- The project is a trading bot with a clear, modular structure.
- The use of Spring Boot and its scheduling features simplifies the orchestration of complex workflows.
- **Critical insight**: Special characters in API requests can cause unexpected behavior, such as data truncation, if not properly encoded.
- The `StockSymbol` enum acts as a whitelist for stocks, but the display names can contain characters that need to be handled carefully.
