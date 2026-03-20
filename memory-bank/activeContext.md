# Active Context

## Current Work Focus
Extended sector ETF tracking from 11 broad SPDR sectors to 20 total ETFs by adding 9 thematic/industry ETFs. Created a centralized `SectorEtfRegistry` to manage all ETF symbols and display names.

## Recent Changes (March 20, 2026)

### Extended Sector ETF Tracking ✅ COMPLETE
- **New `SectorEtfRegistry`** class in `common/` package - central registry for all 20 ETF symbols and display names
- **9 thematic/industry ETFs added**: SMH (Semiconductors), URA (Uranium/Nuclear), SHLD (Cybersecurity), IGV (Software), XOP (Oil & Gas E&P), XHB (Homebuilders), ITA (Aerospace & Defense), XBI (Biotech), TAN (Solar Energy)
- **`SectorRelativeStrengthTracker`**: Refactored to use registry; daily summary now splits into "Sectors" and "Thematic / Industry" sections
- **`SectorMomentumRocTracker`**: Refactored to use `SectorEtfRegistry.allEtfs()` instead of hardcoded map
- **`TailRiskTracker`**: Refactored to use `SectorEtfRegistry.allEtfs()` instead of hardcoded map
- **All 48 tests passing** across the 3 updated test classes

### Previous: Sector RS Streak Tracking (March 19, 2026) ✅
- Streak tracking for consecutive days of outperformance/underperformance vs SPY
- Visual indicators (🟢5, 🔴12) in daily reports
- JSON file persistence

## Architecture Decisions
- **Central ETF Registry**: `SectorEtfRegistry` provides a single source of truth for all ETF symbols and display names, eliminating duplication across trackers
- **Dual-section reports**: Daily RS summary separates broad sectors from thematic/industry ETFs for clearer reporting
- **`Map.of()` + `Map.ofEntries()`**: Registry uses Java immutable maps for thread safety

## Next Steps
- Monitor API rate limits with 20 ETFs (9 additional symbols per polling cycle)
- Consider adding more thematic ETFs in the future (e.g., ARKK Innovation, KWEB China Internet, HACK Cybersecurity, JETS Airlines)
- Historical ETF RS trend analysis