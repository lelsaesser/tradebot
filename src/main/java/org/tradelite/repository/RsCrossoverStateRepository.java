package org.tradelite.repository;

import java.util.Map;
import org.tradelite.service.model.RelativeStrengthData;

/**
 * Repository interface for persisting and retrieving RS crossover state.
 *
 * <p>This repository stores the previous RS/EMA values for each symbol, enabling crossover
 * detection between calculation cycles. The full RS history (200 data points per symbol) is kept
 * in-memory only and rebuilt from price data on startup.
 */
public interface RsCrossoverStateRepository {

    /**
     * Saves or updates RS crossover state for a symbol.
     *
     * @param symbol The stock ticker symbol
     * @param data The RS data containing crossover state to persist
     */
    void save(String symbol, RelativeStrengthData data);

    /**
     * Loads all RS crossover state entries.
     *
     * @return Map of symbol to RelativeStrengthData (only crossover fields populated)
     */
    Map<String, RelativeStrengthData> findAll();
}
