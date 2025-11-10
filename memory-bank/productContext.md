# Product Context

This document explains why this project exists, the problems it solves, how it should work, and the user experience goals.

## Problem
Staying updated on financial markets, including stock and cryptocurrency prices, RSI data, and insider trading information, is a time-consuming process. This bot aims to automate this process and provide timely alerts.

## How it Works
The application is a Spring Boot-based trading bot that performs the following functions:
- **Market Monitoring:** Periodically checks stock and crypto prices using Finnhub and CoinGecko APIs.
- **RSI Data Fetching:** Fetches daily closing prices for stocks and cryptocurrencies to calculate the Relative Strength Index (RSI).
- **Insider Trading Tracking:** Generates a weekly report on insider trading activities.
- **Telegram Integration:** Interacts with a Telegram chat to receive commands and send alerts.
- **Symbol Management:** Allows for ignoring and cleaning up symbols that are no longer of interest.
- **On-Demand RSI:** Users can request the current RSI value for a specific symbol.

## User Experience Goals
The primary user experience is through a Telegram bot. Users can issue commands to the bot to manage their watchlist, request data, and they receive automated alerts based on the configured monitoring.
