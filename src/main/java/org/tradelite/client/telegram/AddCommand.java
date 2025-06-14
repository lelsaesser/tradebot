package org.tradelite.client.telegram;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.tradelite.common.SymbolType;
import org.tradelite.common.TickerSymbol;

@Data
@AllArgsConstructor
public class AddCommand implements TelegramCommand {

    private TickerSymbol symbol;
    private double buyTargetPrice;
    private double sellTargetPrice;
    private SymbolType symbolType;
}
