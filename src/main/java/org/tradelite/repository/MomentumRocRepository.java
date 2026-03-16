package org.tradelite.repository;

import java.util.Optional;
import org.tradelite.service.model.MomentumRocData;

/**
 * Repository interface for persisting and retrieving momentum ROC state data.
 *
 * <p>This repository stores the previous ROC values for each symbol, enabling crossover detection
 * between calculation cycles.
 */
public interface MomentumRocRepository {

    /**
     * Saves or updates momentum ROC data for a symbol.
     *
     * @param symbol The stock ticker symbol
     * @param data The momentum ROC data to persist
     */
    void save(String symbol, MomentumRocData data);

    /**
     * Finds momentum ROC data for a symbol.
     *
     * @param symbol The stock ticker symbol
     * @return Optional containing the momentum data if found
     */
    Optional<MomentumRocData> findBySymbol(String symbol);
}
