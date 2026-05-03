# Active Context

## Current Work Focus

### Migrate target-prices + stock-symbols to SQLite (#326) (May 3, 2026) — COMPLETE
Final PR in the #320 JSON-to-SQLite migration series. Replaced the two highest-risk, user-facing JSON files with SQLite tables.

**Key Changes:**
- New `AssetType` enum (`STOCK`, `COIN`) replaces file-path-based discrimination
- New `target_prices` table (symbol, asset_type, buy_target, sell_target) with `TargetPriceRepository` interface + `SqliteTargetPriceRepository`
- New `stock_symbols` table (ticker, display_name) with `StockSymbolRepository` interface + `SqliteStockSymbolRepository`
- `TargetPriceProvider`: replaced ObjectMapper with TargetPriceRepository, mutation methods take `AssetType` instead of `String filePath`
- `SymbolRegistry`: replaced ObjectMapper/FileInputStream with StockSymbolRepository, in-memory cache reloads from DB on add/remove
- Telegram command processors updated for `AssetType` param
- `@PostConstruct` one-time migration in both classes (reads JSON if table empty + file exists)
- DevDataSeeder seeds both new tables
- JSON files kept on disk for now; cleanup tracked in #359

**Build:** 945 tests pass, spotless clean.
