package org.tradelite.repository;

import java.util.List;
import org.tradelite.common.AssetType;
import org.tradelite.common.SymbolRegistry.StockSymbolEntry;

public interface TrackedSymbolRepository {

    List<StockSymbolEntry> findAll();

    List<StockSymbolEntry> findByAssetType(AssetType type);

    void save(String ticker, String displayName, AssetType type);

    boolean deleteByTickerAndType(String ticker, AssetType type);

    int count();
}
