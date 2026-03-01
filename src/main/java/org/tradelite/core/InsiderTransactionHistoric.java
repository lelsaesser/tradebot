package org.tradelite.core;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.tradelite.common.StockSymbol;

@Data
@AllArgsConstructor
public class InsiderTransactionHistoric {

    private StockSymbol symbol;
    private Map<String, Integer> transactions;
}
