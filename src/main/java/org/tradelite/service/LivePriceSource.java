package org.tradelite.service;

import java.util.Optional;
import org.tradelite.common.TickerSymbol;

public interface LivePriceSource {

    Optional<Double> getPrice(TickerSymbol symbol);

    Optional<Double> getPriceByKey(String symbolKey);
}
