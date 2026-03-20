package org.tradelite.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central registry of all sector and thematic ETFs tracked by the system.
 *
 * <p>This is the single source of truth for ETF symbol-to-display-name mappings used across
 * relative strength tracking, momentum ROC tracking, and tail risk analysis.
 */
public final class SectorEtfRegistry {

    private SectorEtfRegistry() {}

    /** The benchmark ETF symbol. */
    public static final String BENCHMARK_SYMBOL = "SPY";

    /** The benchmark ETF display name. */
    public static final String BENCHMARK_NAME = "S&P 500";

    /** Broad SPDR sector ETF symbols with their display names. */
    public static final Map<String, String> BROAD_SECTOR_ETFS =
            Map.ofEntries(
                    Map.entry("XLK", "Technology"),
                    Map.entry("XLF", "Financials"),
                    Map.entry("XLE", "Energy"),
                    Map.entry("XLV", "Health Care"),
                    Map.entry("XLY", "Cons. Discretionary"),
                    Map.entry("XLP", "Cons. Staples"),
                    Map.entry("XLI", "Industrials"),
                    Map.entry("XLC", "Communication"),
                    Map.entry("XLRE", "Real Estate"),
                    Map.entry("XLB", "Materials"),
                    Map.entry("XLU", "Utilities"));

    /** Thematic / industry-specific ETF symbols with their display names. */
    public static final Map<String, String> THEMATIC_ETFS =
            Map.ofEntries(
                    Map.entry("SMH", "Semiconductors"),
                    Map.entry("SHLD", "Defence Tech"),
                    Map.entry("IGV", "Software"),
                    Map.entry("XOP", "Oil & Gas"),
                    Map.entry("XHB", "Homebuilders"),
                    Map.entry("ITA", "Aerospace & Defence"),
                    Map.entry("XBI", "Biotech"),
                    Map.entry("UFO", "Space"),
                    Map.entry("TAN", "Solar"));

    /** Returns a combined map of all broad sector + thematic ETF names (excludes SPY benchmark). */
    public static Map<String, String> allEtfs() {
        Map<String, String> all = new LinkedHashMap<>(BROAD_SECTOR_ETFS);
        all.putAll(THEMATIC_ETFS);
        return all;
    }

    /** Returns a combined map including the SPY benchmark + all broad sector + thematic ETFs. */
    public static Map<String, String> allEtfsWithBenchmark() {
        Map<String, String> all = new LinkedHashMap<>();
        all.put(BENCHMARK_SYMBOL, BENCHMARK_NAME);
        all.putAll(BROAD_SECTOR_ETFS);
        all.putAll(THEMATIC_ETFS);
        return all;
    }

    /** Returns the set of thematic ETF symbols for section splitting. */
    public static Set<String> thematicSymbols() {
        return THEMATIC_ETFS.keySet();
    }
}
