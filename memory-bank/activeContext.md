# Active Context

## Current Work Focus
✅ **COMPLETED**: Dynamic stock symbol management via Telegram commands (`/add` and `/remove`)
- Full implementation complete with all 270 tests passing
- Build successful - production ready

## Recent Changes (February 2026)

### Major Architecture Changes - COMPLETED

1. **StockSymbol Refactoring**: Converted from enum to regular class ✅
   - Created `StockSymbolRegistry` service for dynamic symbol management
   - Added JSON persistence in `config/stock-symbols.json`
   - Maintains ticker and company display name
   - Thread-safe with ConcurrentHashMap

2. **New Telegram Commands Implementation** ✅
   - `/add TICKER Display_Name` - Adds new stock symbol dynamically
   - `/remove TICKER` - Removes symbol and all associated data (target prices, RSI history)
   - Underscore in display name replaced with space (e.g., `Coherent_Corp` → "Coherent Corp")
   - Both commands default buy/sell targets to 0

3. **Cache Structure Update** ✅
   - `FinnhubPriceEvaluator`: Changed from `EnumMap` to `HashMap<String, PriceQuoteResponse>`
   - Necessary to support dynamic symbol additions
   - Uses ticker string as key

### Implementation Summary

#### New Files Created
- `src/main/java/org/tradelite/service/StockSymbolRegistry.java` - Symbol registry service
- `src/main/java/org/tradelite/client/telegram/AddCommand.java` - Add command model
- `src/main/java/org/tradelite/client/telegram/AddCommandProcessor.java` - Add command handler
- `src/main/java/org/tradelite/client/telegram/RemoveCommand.java` - Remove command model
- `src/main/java/org/tradelite/client/telegram/RemoveCommandProcessor.java` - Remove command handler
- `config/stock-symbols.json` - Persistent storage for 38 stock symbols
- `src/test/java/org/tradelite/service/StockSymbolRegistryTest.java` - Test coverage
- `src/test/java/org/tradelite/client/telegram/AddCommandProcessorTest.java` - Test coverage
- `src/test/java/org/tradelite/client/telegram/RemoveCommandProcessorTest.java` - Test coverage

#### Modified Core Components (14 files)
- `StockSymbol.java` - Now a regular class with ticker/displayName properties
- `TargetPriceProvider.java` - Added `addTargetPrice()`, `removeSymbolFromTargetPrices()` methods
- `RsiService.java` - Added `removeSymbolData()` method for cleanup
- `FinnhubPriceEvaluator.java` - HashMap cache, injected StockSymbolRegistry
- `SetCommandProcessor.java` - Uses StockSymbolRegistry for validation
- `RsiPriceFetcher.java` - Uses StockSymbolRegistry for validation
- `InsiderTracker.java` - Uses StockSymbolRegistry for symbol lookups
- `InsiderPersistence.java` - Uses StockSymbolRegistry for symbol lookups
- `TelegramMessageProcessor.java` - Injected StockSymbolRegistry, updated parseTickerSymbol()
- `BeanConfig.java` - Registered StockSymbolRegistry bean
- `TelegramCommandDispatcher.java` - Registered new command processors

#### Updated Test Files (14 files)
- All tests updated to use StockSymbolRegistry mocks
- Fixed constructor injection issues
- Updated to handle new StockSymbol class structure
- All 270 tests passing ✅

## Testing Commands

### Add New Stock Symbol
```bash
/add COHR Coherent_Corp
# Response: "Stock symbol COHR (Coherent Corp) added successfully with targets: Buy=0.0, Sell=0.0"
```

### Remove Stock Symbol
```bash
/remove COHR
# Response: "Stock symbol COHR removed successfully"
# Cleanup includes: stock-symbols.json, target-prices-stocks.json, RSI historical data
```

### Set Targets (Works with Dynamic Symbols)
```bash
/set buy COHR 150.0
/set sell COHR 200.0
```

## Active Decisions and Considerations

### Symbol Management Strategy
- **Dynamic Registry**: StockSymbolRegistry loads from JSON, allows runtime modifications
- **Persistence**: Changes immediately written to config/stock-symbols.json
- **Backward Compatibility**: Existing commands work with both old hardcoded symbols and new dynamic ones
- **Pre-configured Symbols**: 38 stock symbols included in initial config

### Error Handling
- **Rollback Support**: If target price addition fails, symbol is removed from registry
- **Validation**: Checks for duplicates, null/empty values, symbol existence
- **User Feedback**: Clear Telegram messages for success/failure cases
- **Graceful Degradation**: Invalid symbols logged as warnings, not errors

### Data Cleanup
- **Complete Removal**: `/remove` command deletes:
  1. Symbol from stock-symbols.json
  2. Target prices from target-prices-stocks.json  
  3. RSI historical data via RsiService.removeSymbolData()

### Thread Safety
- StockSymbolRegistry uses ConcurrentHashMap for thread-safe operations
- File writes synchronized in TargetPriceProvider
- Atomic operations for symbol addition/removal

## Important Patterns and Preferences

### Dependency Injection Pattern
All components using StockSymbol lookups now inject `StockSymbolRegistry`:
```java
@Autowired
public MyClass(StockSymbolRegistry stockSymbolRegistry) {
    this.stockSymbolRegistry = stockSymbolRegistry;
}
```

### Symbol Lookup Pattern
```java
Optional<StockSymbol> symbol = stockSymbolRegistry.fromString("AAPL");
if (symbol.isPresent()) {
    StockSymbol stock = symbol.get();
    // Use stock.getTicker(), stock.getDisplayName()
} else {
    // Handle invalid symbol
}
```

### Cache Management Pattern
FinnhubPriceEvaluator cache uses ticker string as key:
```java
Map<String, PriceQuoteResponse> lastPriceCache = new HashMap<>();
lastPriceCache.put(symbol.getTicker(), response);
```

### Command Processing Pattern
1. Parse command in TelegramMessageProcessor
2. Create Command object (AddCommand, RemoveCommand)
3. Dispatch to CommandProcessor
4. Processor validates and executes
5. Send feedback to user via TelegramClient

## Project Insights

### Test Coverage
- **Total Tests**: 270 ✅
- **Test Coverage**: 97% (down from 99% due to new code)
- **Build Status**: SUCCESS ✅
- Mock-heavy approach for external dependencies

### Code Quality
- Using Spotless formatter (run `mvn spotless:apply`)
- Lombok for boilerplate reduction (@Data, @RequiredArgsConstructor, @Slf4j)
- Clear separation of concerns (service layer, persistence, API clients)
- Comprehensive error handling and logging

### Telegram Bot Architecture
- **Command Pattern**: Each command has dedicated Command class and CommandProcessor
- **Dispatcher**: TelegramCommandDispatcher routes to appropriate processor
- **Parser**: TelegramMessageProcessor handles message parsing
- **Validation**: Symbol validation via StockSymbolRegistry
- **Persistence**: Immediate file writes for reliability

### Configuration Files
- `config/stock-symbols.json` - Stock symbol registry (38 symbols)
- `config/target-prices-stocks.json` - Stock target prices
- `config/target-prices-coins.json` - Crypto target prices
- All configs support hot-reload via file watching

## Next Iteration Opportunities

### Future Enhancements (Not Required Now)
- Bulk import/export of stock symbols
- Symbol search/autocomplete
- Historical symbol tracking (when added/removed)
- Symbol categories/tagging
- Validation against external APIs (verify ticker exists)
- Rate limiting for add/remove operations
- Undo functionality for accidental removals

### Performance Optimizations (If Needed)
- Batch file writes if adding many symbols
- Cache invalidation strategies
- Lazy loading of symbol registry
- Periodic registry refresh from file