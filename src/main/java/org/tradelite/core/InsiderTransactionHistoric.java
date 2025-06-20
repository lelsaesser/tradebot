package org.tradelite.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.tradelite.common.StockSymbol;

import java.util.Map;

@Data
@AllArgsConstructor
public class InsiderTransactionHistoric {

    private StockSymbol symbol;
    private Map<String, Integer> transactions;

}
