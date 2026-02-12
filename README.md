## Swing trading support bot.  
![Code Coverage](https://raw.githubusercontent.com/lelsaesser/tradebot/main/.github/badges/badge-coverage.svg?sanitize=true)
[![Code Formatter](https://github.com/lelsaesser/tradebot/actions/workflows/formatter.yml/badge.svg)](https://github.com/lelsaesser/tradebot/actions/workflows/formatter.yml)
[![CodeQL](https://github.com/lelsaesser/tradebot/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/lelsaesser/tradebot/actions/workflows/github-code-scanning/codeql)
[![DevSkim](https://github.com/lelsaesser/tradebot/actions/workflows/devskim.yml/badge.svg)](https://github.com/lelsaesser/tradebot/actions/workflows/devskim.yml)
[![Java CI with Maven](https://github.com/lelsaesser/tradebot/actions/workflows/maven.yml/badge.svg)](https://github.com/lelsaesser/tradebot/actions/workflows/maven.yml)
[![OSV-Scanner](https://github.com/lelsaesser/tradebot/actions/workflows/osv-scanner.yml/badge.svg)](https://github.com/lelsaesser/tradebot/actions/workflows/osv-scanner.yml)

Integrates into several APIs and sends notifications and updates to Telegram chats.

Stock market and price data from Finnhub.io (https://finnhub.io/)  
Crypto market and price data by CoinGecko (https://www.coingecko.com/)  

<img width="710" alt="image" src="https://github.com/user-attachments/assets/edd8f7a4-0ef5-4cf5-9e45-2338eb02dc24" />

## Profiles

- `prod`: uses `FINNHUB_API_KEY`, `COINGECKO_API_KEY`, `TELEGRAM_BOT_TOKEN`, and `TELEGRAM_BOT_GROUP_CHAT_ID`.
- `dev`: uses `FINNHUB_DEV_API_KEY` and `COINGECKO_DEV_API_KEY`, disables schedulers, and routes Telegram output to `config/dev-telegram-messages.log`.

Run with:

```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

Manual dev jobs:

```bash
curl -X POST http://localhost:9090/dev/jobs/stock-monitoring
curl -X POST http://localhost:9090/dev/jobs/crypto-monitoring
curl -X POST http://localhost:9090/dev/jobs/rsi-stock
curl -X POST http://localhost:9090/dev/jobs/rsi-crypto
curl -X POST http://localhost:9090/dev/jobs/insider-report
curl -X POST http://localhost:9090/dev/jobs/sector-rotation
curl -X POST http://localhost:9090/dev/jobs/monthly-api-usage
```
