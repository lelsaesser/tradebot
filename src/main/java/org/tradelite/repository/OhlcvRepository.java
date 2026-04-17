package org.tradelite.repository;

import java.util.List;
import org.tradelite.common.OhlcvRecord;

public interface OhlcvRepository {

    void saveAll(List<OhlcvRecord> records);

    List<OhlcvRecord> findBySymbol(String symbol, int days);

    int deleteBySymbol(String symbol);
}
