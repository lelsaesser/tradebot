package org.tradelite.repository;

import java.util.List;

public interface NewlyAddedSymbolRepository {

    record NewlyAddedSymbol(String ticker, long addedAt) {}

    void insert(String ticker, long addedAt);

    List<NewlyAddedSymbol> findOldest(int limit);

    void deleteAll(List<String> tickers);

    List<String> deleteExpiredReturning(long cutoffTimestamp);

    /**
     * Deletes the row for the given ticker, if present.
     *
     * @param ticker The ticker symbol
     * @return Number of rows deleted (0 or 1)
     */
    int deleteByTicker(String ticker);
}
