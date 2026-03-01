# Product Context

This document explains why this project exists, the problems it solves, how it should work, and the user experience goals.

## Problem
Staying updated on financial markets requires constant monitoring of:
- Stock and cryptocurrency prices against target levels
- RSI (Relative Strength Index) indicators for technical analysis
- Insider trading activities that may signal market moves
- **Sector rotation patterns** that reveal institutional money flow **NEW**

Manual monitoring is time-consuming and can miss critical opportunities. This bot automates the entire process and delivers timely, actionable alerts.

## How it Works
The application is a Spring Boot-based trading bot that operates continuously through scheduled tasks:

### Automated Monitoring
- **Stock Market Monitoring (Every 5 minutes):** Fetches prices from Finnhub API, compares against user-defined target prices, sends Telegram alerts when targets are reached
- **Crypto Market Monitoring (Every 7 minutes):** Fetches prices from CoinGecko API, evaluates against target prices, sends alerts
- **Daily RSI Data Collection:** Fetches historical closing prices daily for RSI calculations, detecting and skipping market holidays
- **Weekly Insider Trading Reports:** Tracks insider transactions from Finnhub, generates comprehensive reports
- **Daily Sector Performance Tracking (10:30 PM ET):** **NEW** Scrapes industry sector performance from FinViz, identifies top/bottom performers

### Intelligent Analysis
- **RSI Calculations:** Automatically calculates 14-period RSI for all monitored symbols
- **Overbought/Oversold Alerts:** Sends notifications when RSI enters critical zones (≥70 overbought, ≤30 oversold)
- **Trend Tracking:** Shows RSI change from previous calculation to indicate momentum
- **Real-Time Data:** Uses cached current prices for accurate on-demand RSI queries
- **Sector Rotation Detection:** **NEW** Identifies industry sectors gaining/losing momentum to spot rotation patterns

### Telegram Integration
Users interact with the bot through Telegram commands:
- **`/rsi <symbol>`:** Get current RSI value instantly (e.g., `/rsi AAPL` or `/rsi bitcoin`)
- **`/add <TICKER> <Display_Name>`:** Add a new stock symbol to monitor
- **`/remove <TICKER>`:** Remove a symbol from watchlist
- **`/show stocks/coins/all`:** Display current watchlist with targets
- **`/set buy/sell <symbol> <price>`:** Configure buy/sell target prices

### Automated Reports (No Command Needed)
- **Daily Sector Performance Report:** Top 5 gainers and losers by daily/weekly performance
- **Weekly Insider Trading Report:** Summary of insider transactions

### Data Management
- **API Metering:** Tracks API usage to stay within rate limits
- **JSON Persistence:** Stores RSI history, target prices, insider transactions, and sector performance in local config files
- **Graceful Error Handling:** Continues operating even with invalid configuration data
- **Historical Data:** Maintains sector performance history for trend analysis

## User Experience Goals
The primary goal is to provide a hands-off monitoring experience where users:
1. **Set and forget:** Configure target prices and symbols once
2. **Receive timely alerts:** Get notified immediately when conditions are met
3. **Query on demand:** Check RSI values anytime via simple commands
4. **Stay informed:** Receive weekly insider trading reports and daily sector updates
5. **Maintain control:** Easily manage watchlist through Telegram commands
6. **Spot market trends:** **NEW** Identify sector rotation patterns without manual research

The bot runs continuously in the background, requiring no manual intervention while keeping users informed of important market developments through their preferred communication channel (Telegram).

## Feature Summary

| Feature | Frequency | Source | Output |
|---------|-----------|--------|--------|
| Stock Price Alerts | Every 5 min | Finnhub | Telegram alert |
| Crypto Price Alerts | Every 7 min | CoinGecko | Telegram alert |
| RSI Monitoring | Daily | Finnhub/CoinGecko | Telegram alert |
| Insider Trading | Weekly | Finnhub | Telegram report |
| **Sector Rotation** | Daily | FinViz | Telegram report **NEW** |

## Sector Rotation Feature (NEW)

### Purpose
Sector rotation is a key indicator of institutional money flow. When money moves from defensive sectors (utilities, consumer staples) to growth sectors (technology, consumer discretionary), it signals risk-on sentiment. The opposite signals risk-off behavior.

### Data Collected
From FinViz industry groups:
- Daily performance change
- Weekly performance
- Monthly performance
- Quarterly performance
- Half-year performance
- Yearly performance
- Year-to-date (YTD) performance

### Report Format
```
📊 *Daily Sector Performance Report*
📅 2026-02-09

📈 *Top 5 Daily Gainers:*
1. Semiconductor: +5.25%
2. Software - Application: +3.50%
3. Internet Content: +2.80%
4. Computer Hardware: +2.45%
5. Biotechnology: +2.20%

📉 *Bottom 5 Daily Losers:*
1. Oil & Gas E&P: -4.20%
2. Coal: -3.80%
3. Utilities - Regulated: -2.50%
4. Banks - Regional: -2.30%
5. Insurance - Specialty: -1.90%

📈 *Top 5 Weekly Gainers:*
...
```

### Future Enhancements
- Multi-week trend analysis
- Sector rotation alerts (significant changes)
- Historical comparison reports
- Correlation with market indices