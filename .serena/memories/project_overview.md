# Tradebot Project Overview

## Purpose
Automated market surveillance system. Monitors financial markets (price, volume, momentum, institutional activity), delivers alerts via Telegram. Covers US stocks, international equities (German/Korean), ETFs, cryptocurrencies.

## Tech Stack
- Java 25, Maven (pom.xml)
- Spring Boot (likely, based on structure)
- Telegram integration for alerts
- APIs: Finnhub.io, TwelveData, CoinGecko

## Project Structure
- `src/` — Java source
- `config/` — configuration files
- `data/` — data files
- `scripts/` — utility scripts
- `memory-bank/` — Memory Bank docs (AGENTS.md pattern)
- `.github/` — CI/CD workflows (Maven, OSV-Scanner, CodeQL, DevSkim, formatter)

## Key Commands
- Build/test: `mvn clean test`
- Format: check `.github/workflows/formatter.yml`
- OSV scan: `.github/workflows/osv-scanner.yml`

## Notes
- Uses AGENTS.md Memory Bank pattern for agent context
- Java 25, UTF-8 encoding
- Darwin/macOS dev environment
