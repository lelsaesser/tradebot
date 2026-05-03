package org.tradelite.repository;

import java.util.List;
import org.tradelite.common.AssetType;
import org.tradelite.common.TargetPrice;

public interface TargetPriceRepository {

    List<TargetPrice> findByAssetType(AssetType type);

    void save(TargetPrice targetPrice, AssetType type);

    boolean deleteBySymbolAndType(String symbol, AssetType type);

    int count();
}
