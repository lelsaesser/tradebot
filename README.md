## Swing trading support bot.  
![Code Coverage](https://raw.githubusercontent.com/lelsaesser/tradebot/main/.github/badges/badge-coverage.svg?sanitize=true)

Integrates into several APIs and sends notifications and updates to Telegram chats.

Stock market and price data from Finnhub.io (https://finnhub.io/)  
Crypto market and price data by CoinGecko (https://www.coingecko.com/)  

<img width="710" alt="image" src="https://github.com/user-attachments/assets/edd8f7a4-0ef5-4cf5-9e45-2338eb02dc24" />

### Local Development Using Stub Profile (WireMock)

**TL;DR**: Set `SPRING_PROFILES_ACTIVE=stub` and run `mvn spring-boot:run`. An embedded WireMock server starts on port `8089` and stubs Finnhub, CoinGecko, and Telegram endpoints. Modify or add JSON mapping files in `src/main/resources/wiremock/mappings` to change responses. Remove the `stub` profile to use real APIs and your actual keys.

You can run the application locally without real API keys by activating the `stub` Spring profile. In this mode an embedded WireMock server (port `8089`) serves deterministic stub responses for Finnhub, CoinGecko, and Telegram.

#### Why use the stub profile?
- Develop UI / logic without consuming external rate limits
- Avoid exposing or needing real API keys
- Produce repeatable test data
- Simulate edge cases easily by adding/changing mappings

#### How it works
- Profile `stub` loads `application-stub.yaml`
- Base URLs for external services are pointed to `http://localhost:8089/...`
- `WireMockConfig` starts an embedded WireMock server with fallback stubs
- JSON mappings in `src/main/resources/wiremock/mappings` override fallbacks

#### Starting in stub mode
```
SPRING_PROFILES_ACTIVE=stub mvn spring-boot:run
```
Or on Windows (PowerShell):
```
$env:SPRING_PROFILES_ACTIVE="stub"; mvn spring-boot:run
```

#### Directory layout for stubs
- `src/main/resources/wiremock/mappings/` – mapping JSON files (request match + response definition)
- `src/main/resources/wiremock/__files/` – optional larger bodies or templates (if you use bodyFileName)

#### Adding a new stub
1. Identify the external URL your client builds (e.g. `/api/v1/quote?symbol=MSFT`).
2. Create a mapping file: `src/main/resources/wiremock/mappings/finnhub-quote-MSFT.json`
3. Match path and query parameters; supply a `jsonBody`.
4. Restart (or use a dynamic admin call if later enabled) to pick up the new mapping.

#### Example (Finnhub quote for AAPL)
```
src/main/resources/wiremock/mappings/finnhub-quote-AAPL.json
{
  "request": {
    "method": "GET",
    "urlPath": "/api/v1/quote",
    "queryParameters": { "symbol": { "equalTo": "AAPL" } }
  },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "jsonBody": {
      "c": 187.42,
      "o": 186.00,
      "h": 188.00,
      "l": 185.50,
      "d": 1.42,
      "dp": 0.76,
      "pc": 186.00
    }
  }
}
```

#### Customizing data
- Duplicate an existing mapping and adjust `jsonBody`
- Use `urlPathPattern` with regex when query strings vary heavily
- Add `response-template` transformer for dynamic echoes (already shown in the Telegram sendMessage stub)

#### Simulating failures
You can temporarily edit or add a mapping with:
```
"response": {
  "status": 500,
  "jsonBody": { "error": "Simulated failure" }
}
```
Then verify your error handling without touching real services.

#### Switching back to real APIs
Run without the stub profile:
```
mvn spring-boot:run
```
(or unset `SPRING_PROFILES_ACTIVE`). Ensure real API keys are available via environment variables or standard Spring property sources.

#### Notes
- Lombok and other build tooling behave the same in stub or real mode.
- If you need dynamic runtime toggling later, consider a feature flag and conditional beans instead of profile-only activation.
- Embedded WireMock uses port 8089; change it in `WireMockConfig` if that conflicts with other tools.

This setup lets you iterate quickly on logic and monitoring features while keeping external dependencies isolated during local development.
