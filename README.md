## Trading, investment and quant analysis bot.
![Code Coverage](https://raw.githubusercontent.com/lelsaesser/tradebot/main/.github/badges/badge-coverage.svg?sanitize=true)
[![Code Formatter](https://github.com/lelsaesser/tradebot/actions/workflows/formatter.yml/badge.svg)](https://github.com/lelsaesser/tradebot/actions/workflows/formatter.yml)
[![CodeQL](https://github.com/lelsaesser/tradebot/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/lelsaesser/tradebot/actions/workflows/github-code-scanning/codeql)
[![DevSkim](https://github.com/lelsaesser/tradebot/actions/workflows/devskim.yml/badge.svg)](https://github.com/lelsaesser/tradebot/actions/workflows/devskim.yml)
[![Java CI with Maven](https://github.com/lelsaesser/tradebot/actions/workflows/maven.yml/badge.svg)](https://github.com/lelsaesser/tradebot/actions/workflows/maven.yml)
[![OSV-Scanner](https://github.com/lelsaesser/tradebot/actions/workflows/osv-scanner.yml/badge.svg)](https://github.com/lelsaesser/tradebot/actions/workflows/osv-scanner.yml)

Integrates into several APIs and sends notifications and updates to Telegram chats.

24/7 market monitoring, quantitative analysis and sector/industry/smart money rotation tracking.

Stock market and price data from Finnhub.io (https://finnhub.io/)  
Crypto market and price data by CoinGecko (https://www.coingecko.com/)  

<img width="710" alt="image" src="https://github.com/user-attachments/assets/edd8f7a4-0ef5-4cf5-9e45-2338eb02dc24" />

## Profiles

- Default profile: production. Running without `SPRING_PROFILES_ACTIVE` uses `FINNHUB_API_KEY`, `COINGECKO_API_KEY`, `TELEGRAM_BOT_TOKEN`, and `TELEGRAM_BOT_GROUP_CHAT_ID`.
- `dev`: opt-in local profile. It disables schedulers, routes Telegram output to `config/dev-telegram-messages.log`, uses a separate local SQLite DB, and calls live Finnhub/CoinGecko APIs via `FINNHUB_DEV_API_KEY` and `COINGECKO_DEV_API_KEY`.

Run production defaults with:

```bash
mvn spring-boot:run
```

Run local development with:

```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

The dev API keys are separate on purpose so local experimentation does not consume the default runtime credentials.

Manual dev jobs:

```bash
curl -X POST http://localhost:9090/dev/jobs/stock-monitoring
curl -X POST http://localhost:9090/dev/jobs/hourly-signals
curl -X POST http://localhost:9090/dev/jobs/crypto-monitoring
curl -X POST http://localhost:9090/dev/jobs/rsi-stock
curl -X POST http://localhost:9090/dev/jobs/rsi-crypto
curl -X POST http://localhost:9090/dev/jobs/insider-report
curl -X POST http://localhost:9090/dev/jobs/sector-rotation
curl -X POST http://localhost:9090/dev/jobs/sector-rs-summary
curl -X POST http://localhost:9090/dev/jobs/tail-risk
curl -X POST http://localhost:9090/dev/jobs/bollinger-report
curl -X POST http://localhost:9090/dev/jobs/monthly-api-usage
curl -X POST http://localhost:9090/dev/jobs/seed-analytics
```

Dev job responses return `200` with `{"status":"ok","job":"..."}` on success and `500` with `{"status":"error","job":"...","message":"check logs"}` if any step in the requested job fails.
