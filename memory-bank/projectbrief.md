# Project Brief

This document outlines the core requirements and goals of the project. It serves as the foundation for all other documentation in the Memory Bank.

## Project Goal

Automated market surveillance system that monitors financial markets across multiple dimensions (price, volume, momentum, institutional activity) and delivers actionable alerts via Telegram. Covers US stocks, international equities (German/Korean), ETFs, and cryptocurrencies.

## Core Features

- **Price Monitoring:** Track stock, ETF, crypto, and international equity prices against configurable buy/sell targets
- **Quantitative Analysis:** EMA structure, Bollinger Bands, VFI (Volume Flow Indicator), Relative Strength vs SPY, Momentum ROC, Tail Risk (kurtosis/skewness)
- **Signal Detection:** EMA pullback buy alerts, institutional accumulation detection (with streak tracking), RS/ROC crossovers, high-change alerts
- **Sector Rotation:** Track 29 sector/thematic ETFs for rotation patterns, momentum shifts, and relative performance
- **Earnings & Insider Reports:** 7-day earnings calendar look-ahead, weekly insider trading reports
- **Telegram Integration:** Command interface for managing watchlist + automated alert/report delivery

## High-Level Requirements

- Run continuously via scheduled tasks (cron-based, timezone-aware)
- Connect to multiple external APIs (Finnhub, CoinGecko, Twelve Data, Yahoo Finance, FinViz)
- Persist all state in SQLite (no external database dependencies)
- Feature toggles gate individual analysis modules independently
- Handle errors gracefully — individual job failures don't crash the system
- Support `dev` profile with synthetic data seeding, manual job triggers, and local Telegram sink
- Maintain 97% test coverage with comprehensive unit and integration tests
