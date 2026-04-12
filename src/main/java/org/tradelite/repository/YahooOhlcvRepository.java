package org.tradelite.repository;

import java.util.List;
import org.tradelite.client.yahoo.dto.YahooOhlcvRecord;

public interface YahooOhlcvRepository {

    void saveAll(List<YahooOhlcvRecord> records);

    List<YahooOhlcvRecord> findBySymbol(String symbol, int days);
}
