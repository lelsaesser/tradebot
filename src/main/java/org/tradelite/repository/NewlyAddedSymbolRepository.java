package org.tradelite.repository;

import java.util.List;

public interface NewlyAddedSymbolRepository {

    record NewlyAddedSymbol(String ticker, long addedAt) {}

    void insert(String ticker, long addedAt);

    List<NewlyAddedSymbol> findOldest(int limit);

    void deleteAll(List<String> tickers);

    List<String> findExpired(long cutoffTimestamp);

    void deleteExpired(long cutoffTimestamp);
}
