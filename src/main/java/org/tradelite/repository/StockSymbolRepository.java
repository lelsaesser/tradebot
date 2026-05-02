package org.tradelite.repository;

import java.util.List;
import org.tradelite.common.SymbolRegistry.StockSymbolEntry;

public interface StockSymbolRepository {

    List<StockSymbolEntry> findAll();

    void save(String ticker, String displayName);

    boolean deleteByTicker(String ticker);

    int count();
}
