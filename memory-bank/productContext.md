# Product Context

This document explains why this project exists, the problems it solves, how it should work, and the user experience goals.

## Problem
Staying updated on financial markets requires constant monitoring of:
- Stock and cryptocurrency prices against target levels
- RSI (Relative Strength Index) indicators for technical analysis
- Insider trading activities that may signal market moves

Manual monitoring is time-consuming and can miss critical opportunities. This bot automates the entire process and delivers timely, actionable alerts.

## How it Works
The application is a Spring Boot-based trading bot that operates continuously through scheduled tasks:

### Automated Monitoring
- **Stock Market Monitoring (Every 5 minutes):** Fetches prices from Finnhub API, compares against user-defined target prices, sends Telegram alerts when targets are reached
- **Crypto Market Monitoring (Every 7 minutes):** Fetches prices from CoinGecko API, evaluates against target prices, sends alerts
- **Daily RSI Data Collection:** Fetches historical closing prices daily for RSI calculations, detecting and skipping market holidays
- **Weekly Insider Trading Reports:** Tracks insider transactions from Finnhub, generates comprehensive reports

### Intelligent Analysis
- **RSI Calculations:** Automatically calculates 14-period RSI for all monitored symbols
- **Overbought/Oversold Alerts:** Sends notifications when RSI enters critical zones (≥70 overbought, ≤30 oversold)
- **Trend Tracking:** Shows RSI change from previous calculation to indicate momentum
- **Real-Time Data:** Uses cached current prices for accurate on-demand RSI queries

### Telegram Integration
Users interact with the bot through Telegram commands:
- **`/rsi <symbol>`:** Get current RSI value instantly (e.g., `/rsi AAPL` or `/rsi bitcoin`)
- **`/add <symbol> <price>`:** Add a new price target to monitor
- **`/remove <symbol>`:** Remove a symbol from watchlist
- **`/show`:** Display current watchlist with targets
- **`/set`:** Configure monitoring parameters

### Data Management
- **API Metering:** Tracks API usage to stay within rate limits
- **JSON Persistence:** Stores RSI history, target prices, and insider transactions in local config files
- **Graceful Error Handling:** Continues operating even with invalid configuration data

## User Experience Goals
The primary goal is to provide a hands-off monitoring experience where users:
1. **Set and forget:** Configure target prices and symbols once
2. **Receive timely alerts:** Get notified immediately when conditions are met
3. **Query on demand:** Check RSI values anytime via simple commands
4. **Stay informed:** Receive weekly insider trading reports
5. **Maintain control:** Easily manage watchlist through Telegram commands

The bot runs continuously in the background, requiring no manual intervention while keeping users informed of important market developments through their preferred communication channel (Telegram).
